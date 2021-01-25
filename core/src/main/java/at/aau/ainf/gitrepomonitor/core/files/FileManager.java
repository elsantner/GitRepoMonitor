package at.aau.ainf.gitrepomonitor.core.files;

import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static at.aau.ainf.gitrepomonitor.core.files.FileManager.RepoList.FOUND;
import static at.aau.ainf.gitrepomonitor.core.files.FileManager.RepoList.WATCH;

public class FileManager {
    private static FileManager instance;

    private Map<UUID, RepositoryInformation> watchlist;
    private Map<UUID, RepositoryInformation> foundRepos;
    private final List<PropertyChangeListener> listenersWatchlist;
    private final List<PropertyChangeListener> listenersFoundRepos;
    private final List<PropertyChangeListener> listenersRepoStatus;

    private final XmlMapper mapper;
    private final File fileDB;
    private final SecureStorage secureStorage;
    private Connection conn;

    public static void setAuthMethod(RepositoryInformation repoInfo) throws IOException {
        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(repoInfo.getPath() + "/.git"))
                .build();

        String originURL = repo.getConfig().getString("remote", "origin", "url");
        if (originURL == null) {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.NONE);
        } else if (originURL.contains("https://")) {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.HTTPS);
        } else {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.SSL);
        }
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

        this.secureStorage = SecureStorage.getImplementation();
        this.mapper = XmlMapper.xmlBuilder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
        this.fileDB = new File(Utils.getProgramHomeDir() + "data.db");
    }

    public void addWatchlistListener(PropertyChangeListener l) { listenersWatchlist.add(l); }

    public void addFoundReposListener(PropertyChangeListener l) { listenersFoundRepos.add(l); }

    public void addRepoStatusListener(PropertyChangeListener l) { listenersRepoStatus.add(l); }

    public boolean removeWatchlistListener(PropertyChangeListener l) { return listenersWatchlist.remove(l); }

    public boolean removeFoundReposListener(PropertyChangeListener l) { return listenersFoundRepos.remove(l); }

    public boolean removeRepoStatusListener(PropertyChangeListener l) { return listenersRepoStatus.remove(l); }

    private void notifyWatchlistChanged() {
        listenersWatchlist.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, getList(WATCH))));
    }
    @JsonIgnore
    private void notifyFoundReposChanged() {
        listenersFoundRepos.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "foundRepos", null, getList(FOUND))));
    }
    @JsonIgnore
    private void notifyRepoStatusChanged(RepositoryInformation repo) {
        listenersRepoStatus.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "repoStatus", null, repo)));
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
        // TODO: Why check paths here?
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
                FileManager.setAuthMethod(repoInfo);
                if (!Utils.validateRepositoryPath(repoInfo.getPath())) {
                    repoInfo.setStatus(RepositoryInformation.RepoStatus.PATH_INVALID);
                }
            } catch (IOException e) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.PATH_INVALID);
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

    // TODO: change to auth_id
    public List<RepositoryInformation> getAuthenticatedRepos() {
        List<RepositoryInformation> authRepos = new ArrayList<>();
        for (RepositoryInformation repo : getAllRepos()) {

            if (repo.isAuthenticated()) {
                authRepos.add(repo);
            }
        }
        return authRepos;
    }

    public void resetAuthMethodAll() {
        for (RepositoryInformation repo : getAllRepos()) {
            repo.setAuthenticated(false);
        }
    }

    public enum RepoList {
        FOUND,
        WATCH
    }

    /**
     * Loads all stored repos.
     * @return True, if data could not be accessed
     */
    public synchronized boolean init() throws ClassNotFoundException, SQLException {
        boolean dbExists = fileDB.exists();

        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + fileDB.getAbsolutePath());
        if (!dbExists) {
            setupDatabase();
        }
        loadRepos();

        // TODO: rework
        checkRepoPathValidity();
        return !checkCredentials();
    }

    public void loadRepos() {
        try {
            Map<UUID, RepositoryInformation> watchlist = new HashMap<>();
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM repo");

            try (ResultSet results = stmt.executeQuery()) {
                while (results.next()) {
                    watchlist.put(UUID.fromString(results.getString("id")),
                            new RepositoryInformation(
                                    UUID.fromString(results.getString("id")),
                                    results.getString("path"),
                                    results.getString("name"),
                                    RepositoryInformation.MergeStrategy.valueOf(results.getString("merge_strat")),
                                    results.getInt("order_idx")));
                }
            }
            this.watchlist = watchlist;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setupDatabase() throws SQLException {
        Statement stmt;
        String sql;

        stmt = conn.createStatement();
        sql = "CREATE TABLE auth " +
                "(id TEXT PRIMARY KEY     NOT NULL," +
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
                " order_idx      INT, " +
                " FOREIGN KEY (auth_id) REFERENCES auth (id) )";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    public void clearAllAuthRequirements() {
        for (RepositoryInformation repo : getAllRepos()) {
            repo.setAuthenticated(false);
            updateInDB(repo);
        }
    }

    /**
     * Check if the credentials file is present if authenticated repos exist.
     * @return False, if credentials are required but no credentials file exists (was deleted).
     */
    // TODO: rework
    private boolean checkCredentials() {
        List<RepositoryInformation> authRequiredRepos = getAuthenticatedRepos();
        if (!secureStorage.isIntact(authRequiredRepos)) {
            resetAuthMethodAll();
            return false;
        } else {
            return true;
        }
    }

    public synchronized void addToFoundRepos(RepositoryInformation repo) {
        if (!getWatchlist().contains(repo)) {
            addToList(FOUND, repo);
            addToDB(repo);
        }
    }

    public synchronized void foundToWatchlist(List<RepositoryInformation> repos) {
        addToList(WATCH, repos);
        removeFromList(FOUND, repos);
        for (RepositoryInformation repo: repos) {
            updateInDB(repo);
        }
    }

    public synchronized void watchlistToFound(List<RepositoryInformation> repos) {
        removeFromList(WATCH, repos);
        addToList(FOUND, repos);
        for (RepositoryInformation repo: repos) {
            updateInDB(repo);
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
                    "INSERT INTO repo (id, path, name, merge_strat, order_idx, list, auth_id) VALUES (?,?,?,?,?,?,?)");
            stmt.setString(1, repo.getID().toString());
            stmt.setString(2, repo.getPath());
            stmt.setString(3, repo.getName());
            stmt.setString(4, repo.getMergeStrategy().name());
            stmt.setInt(5, repo.getCustomOrderIndex());
            stmt.setString(6, getListName(repo).name());
            stmt.setString(7, null);    // TODO: set auth_id

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
                    "UPDATE repo SET path=?, name=?, merge_strat=?, order_idx=?, list=?, auth_id=? WHERE id=?");
            stmt.setString(1, repo.getPath());
            stmt.setString(2, repo.getName());
            stmt.setString(3, repo.getMergeStrategy().name());
            stmt.setInt(4, repo.getCustomOrderIndex());
            stmt.setString(5, getListName(repo).name());
            stmt.setString(6, null);    // TODO: set auth_id
            stmt.setString(7, repo.getID().toString());

            stmt.executeUpdate();
            Logger.getAnonymousLogger().info("UPDATED in DB: " + repo.getPath());
            repo.setPersistentValueChanged(false);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void deleteFromDB(RepositoryInformation repo) {
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
     * Enquires whether any repository on the watchlist has a authentication method (!= NONE) specified.
     * @return True, if any repository on the watchlist has a authentication method specified
     */
    public boolean isWatchlistAuthenticationRequired() {
        for (RepositoryInformation repo : getList(WATCH)) {
            if (repo.isAuthenticated())
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
}
