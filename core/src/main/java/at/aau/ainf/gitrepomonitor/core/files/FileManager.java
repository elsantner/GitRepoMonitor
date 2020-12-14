package at.aau.ainf.gitrepomonitor.core.files;

import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import static at.aau.ainf.gitrepomonitor.core.files.RepoListWrapper.RepoList.FOUND;
import static at.aau.ainf.gitrepomonitor.core.files.RepoListWrapper.RepoList.WATCH;

public class FileManager {
    private static FileManager instance;

    private XmlMapper mapper;
    private File fileRepoLists;
    private RepoListWrapper repoListWrapper;
    private boolean repoListInitialized = false;
    private SecureStorage secureStorage;

    public static synchronized FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }

    private FileManager() {
        this.secureStorage = SecureStorage.getImplementation();
        this.mapper = XmlMapper.xmlBuilder().build();
        this.fileRepoLists = new File(Utils.getProgramHomeDir() + "repolists.xml");
    }

    public boolean isInitialized() {
        return repoListInitialized;
    }

    /**
     * Loads all stored repos.
     * @return True, if repo auth method was reset to NONE as a consequence of missing credentials file
     */
    public synchronized boolean init() throws IOException {
        try {
            repoListWrapper = mapper.readValue(fileRepoLists, new TypeReference<RepoListWrapper>(){});
        } catch (IOException e) {
            fileRepoLists.getParentFile().mkdirs();
            fileRepoLists.createNewFile();
            repoListWrapper = new RepoListWrapper();
            persistRepoLists();
        }
        repoListWrapper.checkRepoPathValidity();
        repoListInitialized = true;
        return !checkCredentials();
    }

    /**
     * Check if the credentials file is present if authenticated repos exist.
     * @return False, if credentials are required but no credentials file exists (was deleted).
     */
    private boolean checkCredentials() {
        List<RepositoryInformation> authRequiredRepos = repoListWrapper.getAuthenticatedRepos();
        if (!secureStorage.isIntact(authRequiredRepos)) {
            repoListWrapper.resetAuthMethodAll();
            persistRepoLists();
            return false;
        } else {
            return true;
        }
    }

    private RepoListWrapper getRepoListWrapper() {
        if (!repoListInitialized) {
            throw new IllegalStateException("file manager has not been initialized");
        }
        return this.repoListWrapper;
    }

    public synchronized void addWatchlistListener(PropertyChangeListener l) {
        getRepoListWrapper().addWatchlistListener(l);
    }

    public synchronized void addFoundReposListener(PropertyChangeListener l) {
        getRepoListWrapper().addFoundReposListener(l);
    }

    public synchronized void addRepoStatusListener(PropertyChangeListener l) {
        getRepoListWrapper().addRepoStatusListener(l);
    }

    public synchronized boolean removeWatchlistListener(PropertyChangeListener l) {
        return getRepoListWrapper().removeWatchlistListener(l);
    }

    public synchronized boolean removeFoundReposListener(PropertyChangeListener l) {
        return getRepoListWrapper().removeFoundReposListener(l);
    }

    public synchronized boolean removeRepoStatusListener(PropertyChangeListener l) {
        return getRepoListWrapper().removeRepoStatusListener(l);
    }

    public synchronized void addToFoundRepos(RepositoryInformation repo) {
        if (!repoListWrapper.getWatchlist().contains(repo)) {
            repo.setDateAdded(Calendar.getInstance().getTime());
            repoListWrapper.addToList(FOUND, repo);
            persistRepoLists();
        }
    }

    public synchronized void addToWatchlist(List<RepositoryInformation> repos) {
        repoListWrapper.addToList(WATCH, repos);
        repoListWrapper.removeFromList(FOUND, repos);
        persistRepoLists();
    }

    public synchronized void addToWatchlist(RepositoryInformation repo) {
        repoListWrapper.addToList(WATCH, repo);
        repoListWrapper.removeFromList(FOUND, repo);
        persistRepoLists();
    }

    public synchronized void removeFromWatchlist(List<RepositoryInformation> repos) {
        repoListWrapper.removeFromList(WATCH, repos);
        repoListWrapper.addToList(FOUND, repos);
        persistRepoLists();
    }

    public synchronized void removeFromWatchlist(RepositoryInformation repo) {
        repoListWrapper.removeFromList(WATCH, repo);
        repoListWrapper.addToList(FOUND, repo);
        persistRepoLists();
    }

    public RepositoryInformation getRepo(String path) {
        return (RepositoryInformation) repoListWrapper.getRepo(path).clone();
    }

    /**
     * This method updates a given RepositoryInformation object in the persistent storage and
     * informs all listeners of the change.
     * @param originalPath Original path of this repository before editing
     * @param updatedInfo Updated RepositoryInformation
     */
    public synchronized void editRepo(String originalPath, RepositoryInformation updatedInfo) throws NoSuchElementException {
        RepoListWrapper.RepoList repoList = repoListWrapper.getListName(originalPath);
        if (repoList == null) {
            throw new NoSuchElementException("Repo at given path not found");
        }
        if (!originalPath.equals(updatedInfo.getPath()) && repoListWrapper.exists(updatedInfo)) {
            throw new IllegalArgumentException("Repo at updated path already exists");
        }
        repoListWrapper.removeFromList(repoList, new RepositoryInformation(originalPath));
        repoListWrapper.addToList(repoList, updatedInfo);

        // only persist repo lists if persistent properties were changed
        if (updatedInfo.isPersistentValueChanged()) {
            persistRepoLists();
            updatedInfo.setPersistentValueChanged(false);
        }
    }

    public void updateRepoStatus(String path, RepositoryInformation.RepoStatus status) {
        repoListWrapper.updateRepoStatus(path, status);
    }

    public void setNewChanges(String path, int newCommitCount) {
        repoListWrapper.setNewChanges(path, newCommitCount);
    }

    public synchronized void deleteRepo(RepositoryInformation repo) {
        RepoListWrapper.RepoList repoList = repoListWrapper.getListName(repo);
        if (repoList == null) {
            throw new NoSuchElementException("Repo at given path not found");
        }
        repoListWrapper.removeFromList(repoList, repo);
        persistRepoLists();
    }

    private synchronized void persistRepoLists() {
        try {
            mapper.writeValue(fileRepoLists, repoListWrapper);
            Logger.getAnonymousLogger().info("Wrote repolists to " + fileRepoLists.getAbsolutePath());
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public List<RepositoryInformation> getWatchlist() {
        return List.copyOf(repoListWrapper.getWatchlist());
    }

    public List<RepositoryInformation> getFoundRepos() {
        return List.copyOf(repoListWrapper.getFoundRepos());
    }

    /**
     * Enquires whether any repository on the watchlist has a authentication method (!= NONE) specified.
     * @return True, if any repository on the watchlist has a authentication method specified
     */
    public boolean isWatchlistAuthenticationRequired() {
        for (RepositoryInformation repo : repoListWrapper.getList(WATCH)) {
            if (repo.isAuthenticated())
                return true;
        }
        return false;
    }

    public List<RepositoryInformation> getAllAuthenticatedRepos() {
        return repoListWrapper.getAuthenticatedRepos();
    }
}
