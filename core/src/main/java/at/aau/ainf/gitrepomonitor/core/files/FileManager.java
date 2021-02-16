package at.aau.ainf.gitrepomonitor.core.files;

import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SslCredentials;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.aau.ainf.gitrepomonitor.core.files.FileManager.RepoList.FOUND;
import static at.aau.ainf.gitrepomonitor.core.files.FileManager.RepoList.WATCH;

/**
 * Provides access to persistently stored data.
 */
public class FileManager implements FileMonitor.Listener {
    private static FileManager instance;

    private Map<UUID, RepositoryInformation> watchlist;
    private Map<UUID, RepositoryInformation> foundRepos;
    private final List<PropertyChangeListener> listenersWatchlist;
    private final List<PropertyChangeListener> listenersFoundRepos;
    private final List<PropertyChangeListener> listenersRepoStatus;
    private final List<PropertyChangeListener> listenersAuthCred;
    private FileErrorListener fileErrorListener;

    private Connection conn;
    private final ThreadPoolExecutor executor;
    private FileMonitor fileMonitor;

    public enum RepoList {
        FOUND,
        WATCH
    }

    public static synchronized FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }

    private FileManager() {
        this.watchlist = new HashMap<>();
        this.foundRepos = new HashMap<>();
        this.listenersWatchlist = new ArrayList<>();
        this.listenersFoundRepos = new ArrayList<>();
        this.listenersRepoStatus = new ArrayList<>();
        this.listenersAuthCred = new ArrayList<>();
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Loads all stored repos.
     */
    public synchronized void init() throws ClassNotFoundException, SQLException {
        openDatabaseConnection();
        setupFileMonitor();
        loadRepos();
        checkRepoPathValidity();
        loadLastCommits();
    }

    public void addWatchlistListener(PropertyChangeListener l) { listenersWatchlist.add(l); }

    public void addFoundReposListener(PropertyChangeListener l) { listenersFoundRepos.add(l); }

    public void addRepoStatusListener(PropertyChangeListener l) { listenersRepoStatus.add(l); }

    public void addAuthCredListener(PropertyChangeListener l) { listenersAuthCred.add(l); }

    public boolean removeWatchlistListener(PropertyChangeListener l) { return listenersWatchlist.remove(l); }

    public boolean removeFoundReposListener(PropertyChangeListener l) { return listenersFoundRepos.remove(l); }

    public boolean removeRepoStatusListener(PropertyChangeListener l) { return listenersRepoStatus.remove(l); }

    public boolean removeAuthCredListener(PropertyChangeListener l) { return listenersAuthCred.remove(l); }

    public void setFileErrorListener(FileErrorListener fileErrorListener) {
        this.fileErrorListener = fileErrorListener;
    }

    private void notifyWatchlistChanged() {
        listenersWatchlist.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, getList(WATCH))));
    }

    private void notifyFoundReposChanged() {
        listenersFoundRepos.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "foundRepos", null, getList(FOUND))));
    }

    private void notifyRepoStatusChanged(RepositoryInformation repo) {
        listenersRepoStatus.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "repoStatus", null, repo)));
    }

    private void notifyAuthCredChanged() {
        listenersAuthCred.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "authCred", null, null)));
    }

    public List<RepositoryInformation> getList(RepoList list) {
        if (list == WATCH) {
            return getWatchlist();
        } else if (list == FOUND) {
            return getFoundRepos();
        } else {
            return null;
        }
    }

    public List<RepositoryInformation> getWatchlist() {
        if (watchlist == null) {
            watchlist = new HashMap<>();
        }
        return new ArrayList<>(watchlist.values());
    }

    public List<RepositoryInformation> getFoundRepos() {
        if (foundRepos == null) {
            foundRepos = new HashMap<>();
        }
        return new ArrayList<>(foundRepos.values());
    }

    public Set<RepositoryInformation> getAllRepos() {
        return Stream.concat(watchlist.values().stream(),
                foundRepos.values().stream()).collect(Collectors.toSet());
    }

    public boolean exists(RepositoryInformation repo) {
        return getListName(repo) != null;
    }

    public RepoList getListName(UUID repoID) {
        return getListName(new RepositoryInformation(repoID));
    }

    public RepoList getListName(RepositoryInformation repo) {
        if (watchlist.containsKey(repo.getID())) {
            return WATCH;
        } else if (foundRepos.containsKey(repo.getID())) {
            return FOUND;
        } else {
            return null;
        }
    }

    public void addToList(RepoList list, Collection<RepositoryInformation> repos) {
        checkRepoPathValidity(repos);
        switch (list) {
            case WATCH:
                repos.forEach(repoInfo -> watchlist.put(repoInfo.getID(), repoInfo));
                notifyWatchlistChanged();
                break;
            case FOUND:
                repos.forEach(repoInfo -> foundRepos.put(repoInfo.getID(), repoInfo));
                notifyFoundReposChanged();
                break;
        }
    }

    public void addToList(RepoList list, RepositoryInformation repo) {
        addToList(list, Collections.singletonList(repo));
    }

    public void removeFromList(RepoList list, Collection<RepositoryInformation> repos) {
        switch (list) {
            case WATCH:
                repos.forEach(repoInfo -> watchlist.remove(repoInfo.getID()));
                notifyWatchlistChanged();
                break;
            case FOUND:
                repos.forEach(repoInfo -> foundRepos.remove(repoInfo.getID()));
                notifyFoundReposChanged();
                break;
        }
    }

    public void removeFromList(RepoList list, RepositoryInformation repo) {
        removeFromList(list, Collections.singletonList(repo));
    }

    /**
     * Sets pathValid property of all repos
     */
    public void checkRepoPathValidity() {
        checkRepoPathValidity(Stream.concat(watchlist.values().stream(),
                foundRepos.values().stream()).collect(Collectors.toList()));
    }

    private void checkRepoPathValidity(Collection<RepositoryInformation> reposToCheck) {
        for (RepositoryInformation repoInfo : reposToCheck) {
            try {
                GitManager.setAuthMethod(repoInfo);
                if (!Utils.validateRepositoryPath(repoInfo.getPath())) {
                    repoInfo.setStatus(RepositoryInformation.RepoStatus.PATH_INVALID);
                }
            } catch (IOException e) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.PATH_INVALID);
            }
        }
    }

    private void loadLastCommits() {
        GitManager gitManager = GitManager.getInstance();
        for (RepositoryInformation repoInfo : Stream.concat(watchlist.values().stream(),
                foundRepos.values().stream()).collect(Collectors.toList())) {

            try {
                repoInfo.setLastCommit(gitManager.getLastCommit(repoInfo));
            } catch (Exception e) {
                repoInfo.setLastCommit(null);
            }
        }
    }

    public RepositoryInformation getRepo(UUID id) {
        RepositoryInformation repo = watchlist.get(id);
        if (repo == null) {
            repo = foundRepos.get(id);
        }
        return repo;
    }

    public void updateRepoStatus(UUID id, RepositoryInformation.RepoStatus status) {
        RepositoryInformation repo = getRepo(id);
        if (repo == null) {
            throw new NoSuchElementException();
        }
        repo.setStatus(status);
        notifyRepoStatusChanged(repo);
    }

    public void setNewChanges(UUID id, int newCommitCount) {
        RepositoryInformation repo = getRepo(id);
        if (repo == null) {
            throw new NoSuchElementException();
        }
        repo.setNewChanges(newCommitCount);
        notifyRepoStatusChanged(repo);
    }

    public List<RepositoryInformation> getAuthenticatedRepos() {
        List<RepositoryInformation> authRepos = new ArrayList<>();
        for (RepositoryInformation repo : getAllRepos()) {

            if (repo.getAuthID() != null) {
                authRepos.add(repo);
            }
        }
        return authRepos;
    }

    public void resetAuthAll() {
        for (RepositoryInformation repo : getAllRepos()) {
            repo.setAuthID(null);
            updateInDB(repo);
        }
    }

    public void clearAllAuthStrings() {
        resetAuthAll();
        try {
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM auth");
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static File getDBFile() {
        return new File(StoragePath.getCurrentPath() + "data.db");
    }

    /**
     * Change data location to newPath.
     * If there is no data.db at newPath, move the current one over there.
     * If there is a data.db at newPath, read that data. (data.db at old location is not moved)
     */
    public void storagePathChanged() {
        try {
            if (fileMonitor != null) {
                fileMonitor.stop();
            }
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }

            init();
            notifyFoundReposChanged();
            notifyWatchlistChanged();
            notifyAuthCredChanged();
            SecureStorage.getImplementation().clearCachedMasterPassword();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Check if DB file exists and has RW permissions
     * @return
     */
    public boolean isDatabaseAccessible() {
        return getDBFile().exists() && getDBFile().canRead() && getDBFile().canWrite();
    }

    public synchronized void openDatabaseConnection() throws ClassNotFoundException, SQLException {
        boolean dbExists = getDBFile().exists();
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + getDBFile().getAbsolutePath());

        // if DB was just created, create tables
        if (!dbExists) {
            setupDatabase();
        }
    }

    /**
     * Setup monitor for database file.
     * This aims to detect disconnects of external storage devices used to store program data.
     */
    private void setupFileMonitor() {
        fileMonitor = new FileMonitor(getDBFile(), this);
        fileMonitor.start();
    }

    @Override
    public void fileUnavailable(File file) {
        if (fileErrorListener != null) {
            fileErrorListener.fileUnavailable(file);
        }
    }

    /**
     * Load all stored repos from DB into transient collections.
     */
    public void loadRepos() {
        try {
            Map<UUID, RepositoryInformation> newWatchlist = new HashMap<>();
            Map<UUID, RepositoryInformation> newFoundRepos = new HashMap<>();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM repo");

            try (ResultSet results = stmt.executeQuery()) {
                while (results.next()) {
                    String authID = results.getString("auth_id");
                    Map<UUID, RepositoryInformation> list = RepoList.valueOf(results.getString("list")) == WATCH ?
                            newWatchlist : newFoundRepos;

                    list.put(UUID.fromString(results.getString("id")),
                            new RepositoryInformation(
                                    UUID.fromString(results.getString("id")),
                                    results.getString("path"),
                                    results.getString("name"),
                                    RepositoryInformation.MergeStrategy.valueOf(results.getString("merge_strat")),
                                    authID != null ? UUID.fromString(authID) : null));
                }
            }
            this.watchlist = newWatchlist;
            this.foundRepos = newFoundRepos;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Create required tables.
     * @throws SQLException
     */
    private void setupDatabase() throws SQLException {
        Statement stmt;
        String sql;

        stmt = conn.createStatement();
        sql = "CREATE TABLE auth " +
                "(id TEXT PRIMARY KEY     NOT NULL," +
                " name           TEXT     NOT NULL, " +
                " type           CHAR(5)  NOT NULL, " +
                " enc_value      TEXT)";
        stmt.executeUpdate(sql);
        stmt.close();

        stmt = conn.createStatement();
        sql = "CREATE TABLE repo " +
                "(id TEXT PRIMARY KEY     NOT NULL," +
                " path           TEXT     UNIQUE NOT NULL, " +
                " list           CHAR(5)  NOT NULL, " +
                " name           TEXT, " +
                " merge_strat    CHAR(50), " +
                " auth_id        TEXT, " +
                " FOREIGN KEY (auth_id) REFERENCES auth (id) )";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    public synchronized void addToFoundRepos(RepositoryInformation repo) {
        // only add to found repos if watchlist does not already contain repo
        if (!getWatchlist().contains(repo)) {
            addToList(FOUND, repo);
            addToDB(repo);
        }
    }

    public void addToFoundReposAsync(RepositoryInformation repositoryInformation, FileOperationCallback cb) {
        executor.submit(() -> {
            try {
                addToFoundRepos(repositoryInformation);
                cb.finished(true, null);
            } catch (Exception e) {
                cb.finished(false, e);
            }
        });
    }

    public synchronized void addToWatchlist(RepositoryInformation repo) {
        // only add to watchlist if it does not already contain repo
        if (!getWatchlist().contains(repo)) {
            // if repo is in found repos, remove it from found repo list first
            if (getFoundRepos().contains(repo)) {
                // repo is removed by ID, so get the old id by path
                RepositoryInformation existingRepo = getFoundRepos().stream().findAny()
                        .filter(r -> r.getPath().equals(repo.getPath())).get();
                removeFromList(FOUND, existingRepo);
                deleteFromDB(existingRepo);
            }
            addToList(WATCH, repo);
            addToDB(repo);
        }
    }

    public synchronized void foundToWatchlist(List<RepositoryInformation> repos) {
        addToList(WATCH, repos);
        removeFromList(FOUND, repos);
        for (RepositoryInformation repo: repos) {
            executor.submit(() -> updateInDB(repo));
        }
    }

    public synchronized void watchlistToFound(List<RepositoryInformation> repos) {
        removeFromList(WATCH, repos);
        addToList(FOUND, repos);
        for (RepositoryInformation repo: repos) {
            executor.submit(() -> updateInDB(repo));
        }
    }

    /**
     * This method updates a given RepositoryInformation object in the persistent storage and
     * informs all listeners of the change.
     * @param originalPath Original path of this repository before editing
     * @param updatedInfo Updated RepositoryInformation
     */
    public synchronized void editRepo(String originalPath, RepositoryInformation updatedInfo) throws NoSuchElementException {
        RepoList repoList = getListName(updatedInfo.getID());
        if (repoList == null) {
            throw new NoSuchElementException("Repo at given path not found");
        }
        if (!originalPath.equals(updatedInfo.getPath()) && exists(updatedInfo)) {
            throw new IllegalArgumentException("Repo at updated path already exists");
        }
        removeFromList(repoList, new RepositoryInformation(originalPath));
        addToList(repoList, updatedInfo);

        // only persist repo lists if persistent properties were changed
        if (updatedInfo.isPersistentValueChanged()) {
            updateInDB(updatedInfo);
            updatedInfo.setPersistentValueChanged(false);
        }
    }

    private void addToDB(RepositoryInformation repo) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO repo (id, path, name, merge_strat, list, auth_id) VALUES (?,?,?,?,?,?)");
            stmt.setString(1, repo.getID().toString());
            stmt.setString(2, repo.getPath());
            stmt.setString(3, repo.getName());
            stmt.setString(4, repo.getMergeStrategy().name());
            stmt.setString(5, getListName(repo).name());
            stmt.setString(6, Utils.toStringOrNull(repo.getAuthID()));

            stmt.executeUpdate();
            Logger.getAnonymousLogger().info("ADDED to DB: " + repo.getPath());
            repo.setPersistentValueChanged(false);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void updateInDB(RepositoryInformation repo) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE repo SET path=?, name=?, merge_strat=?, list=?, auth_id=? WHERE id=?");
            stmt.setString(1, repo.getPath());
            stmt.setString(2, repo.getName());
            stmt.setString(3, repo.getMergeStrategy().name());
            stmt.setString(4, getListName(repo).name());
            stmt.setString(5, Utils.toStringOrNull(repo.getAuthID()));
            stmt.setString(6, repo.getID().toString());

            stmt.executeUpdate();
            Logger.getAnonymousLogger().info("UPDATED in DB: " + repo.getPath());
            repo.setPersistentValueChanged(false);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void deleteFromDB(RepositoryInformation repo) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM repo WHERE id=?");
            stmt.setString(1, repo.getID().toString());
            stmt.executeUpdate();
            Logger.getAnonymousLogger().info("DELETED from DB: " + repo.getPath());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public synchronized void deleteRepo(RepositoryInformation repo) {
        RepoList repoList = getListName(repo);
        if (repoList == null) {
            throw new NoSuchElementException("Repo at given path not found");
        }
        removeFromList(repoList, repo);
        deleteFromDB(repo);
    }

    /**
     * Enquires whether any repository on the watchlist has a authID specified (!= null).
     * @return True, if any repository on the watchlist has a authID specified.
     */
    public boolean isWatchlistAuthenticationRequired() {
        for (RepositoryInformation repo : getList(WATCH)) {
            if (repo.getAuthID() != null)
                return true;
        }
        return false;
    }

    public void applyMergeStratToAllRepos(RepositoryInformation.MergeStrategy strat) {
        for (RepositoryInformation repo : getAllRepos()) {
            repo.setMergeStrategy(strat);
            updateInDB(repo);
        }
    }

    public void storeAuthentication(AuthenticationCredentials authCred, String encString) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO auth (id, name, type, enc_value) VALUES (?,?,?,?)");
            stmt.setString(1, authCred.getID().toString());
            stmt.setString(2, authCred.getName());
            stmt.setString(3, authCred.getAuthMethod().name());
            stmt.setString(4, encString);

            stmt.executeUpdate();
            notifyAuthCredChanged();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Get authentication string with provided ID
     * @param authID ID of auth string
     * @return null if not found
     */
    public String readAuthenticationString(UUID authID) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT enc_value FROM auth WHERE id=?");
            stmt.setString(1, authID.toString());

            try (ResultSet results = stmt.executeQuery()) {
                results.next();
                return results.getString("enc_value");
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    public void updateAuthentication(AuthenticationCredentials authCred, String encString) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE auth SET name=?, type=?, enc_value=? WHERE id=?");
            stmt.setString(1, authCred.getName());
            stmt.setString(2, authCred.getAuthMethod().name());
            stmt.setString(3, encString);
            stmt.setString(4, authCred.getID().toString());

            stmt.executeUpdate();
            notifyAuthCredChanged();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void updateAuthentication(UUID authID, String encString) {
        try {
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE auth SET enc_value=? WHERE id=?");
            stmt.setString(1, encString);
            stmt.setString(2, authID.toString());

            stmt.executeUpdate();
            notifyAuthCredChanged();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Delete auth info and set the auth_id of all using repos to null.
     * @param authID ID of auth string
     */
    public void deleteAuthentication(UUID authID) {
        try {
            // clear auth id from all affected repos in DB ...
            PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE repo SET auth_id=NULL WHERE auth_id=?");
            stmt.setString(1, authID.toString());
            stmt.executeUpdate();

            // ... and in transient "Cache"
            for (RepositoryInformation repo : getAllRepos()) {
                if (authID.equals(repo.getAuthID())) {
                    repo.setAuthID(null);
                    repo.setPersistentValueChanged(false);
                }
            }

            stmt = conn.prepareStatement(
                    "DELETE FROM auth WHERE id=?");
            stmt.setString(1, authID.toString());
            stmt.executeUpdate();

            notifyAuthCredChanged();
            notifyWatchlistChanged();
            notifyFoundReposChanged();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns all authentication strings without MP_SET
     * @return
     */
    public Map<UUID, String> getAllAuthenticationStrings() {
        return getAllAuthenticationStrings(false);
    }

    /**
     * Returns all authentication strings
     * @param includeMPSet Include MP_SET or not
     * @return
     */
    public Map<UUID, String> getAllAuthenticationStrings(boolean includeMPSet) {
        Map<UUID, String> authStrings = new HashMap<>();
        try {
            // exclude MP_SET entry
            PreparedStatement stmt = conn.prepareStatement("SELECT id, enc_value FROM auth" +
                            (includeMPSet ? "" : "WHERE type <> 'NONE'"));

            try (ResultSet results = stmt.executeQuery()) {
                while (results.next()) {
                    authStrings.put(UUID.fromString(results.getString("id")),
                            results.getString("enc_value"));
                }
            }
            return authStrings;
        } catch (SQLException ex) {
            return new HashMap<>();
        }
    }

    public List<AuthenticationCredentials> getAllAuthenticationCredentials() {
        List<AuthenticationCredentials> authCreds = new ArrayList<>();
        try {
            // exclude MP_SET entry
            PreparedStatement stmt = conn.prepareStatement("SELECT id, name, type FROM auth WHERE type <> 'NONE'");

            try (ResultSet results = stmt.executeQuery()) {
                while (results.next()) {
                    AuthenticationCredentials authCred;
                    if (results.getString("type").equals(RepositoryInformation.AuthMethod.HTTPS.name())) {
                        authCred = new HttpsCredentials();
                    } else {
                        authCred = new SslCredentials();
                    }
                    authCred.setID(UUID.fromString(results.getString("id")));
                    authCred.setName(results.getString("name"));
                    authCreds.add(authCred);
                }
            }
            return authCreds;
        } catch (SQLException ex) {
            return new ArrayList<>();
        }
    }

    public List<AuthenticationCredentials> getAllAuthenticationCredentials(RepositoryInformation.AuthMethod authMethod) {
        List<AuthenticationCredentials> authCreds = new ArrayList<>();
        try {
            // exclude MP_SET entry
            PreparedStatement stmt = conn.prepareStatement("SELECT id, name FROM auth WHERE type=?");
            stmt.setString(1, authMethod.name());

            try (ResultSet results = stmt.executeQuery()) {
                while (results.next()) {
                    AuthenticationCredentials authCred;
                    if (authMethod == RepositoryInformation.AuthMethod.HTTPS) {
                        authCred = new HttpsCredentials();
                    } else {
                        authCred = new SslCredentials();
                    }
                    authCred.setID(UUID.fromString(results.getString("id")));
                    authCred.setName(results.getString("name"));
                    authCreds.add(authCred);
                }
            }
            return authCreds;
        } catch (SQLException ex) {
            return new ArrayList<>();
        }
    }

    public int getUsingRepoCount(UUID authID) {
        try {
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(id) FROM repo WHERE auth_id=?");
            stmt.setString(1, authID.toString());

            try (ResultSet results = stmt.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        } catch (SQLException ex) {
            return 0;
        }
    }
}
