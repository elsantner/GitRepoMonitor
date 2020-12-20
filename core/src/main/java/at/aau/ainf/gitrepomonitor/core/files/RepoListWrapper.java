package at.aau.ainf.gitrepomonitor.core.files;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class RepoListWrapper {
    private Map<String, RepositoryInformation> watchlist;
    private Map<String, RepositoryInformation> foundRepos;
    private List<PropertyChangeListener> listenersWatchlist;
    private List<PropertyChangeListener> listenersFoundRepos;
    private List<PropertyChangeListener> listenersRepoStatus;

    public RepoListWrapper() {
        this.watchlist = new HashMap<>();
        this.foundRepos = new HashMap<>();
        this.listenersWatchlist = new ArrayList<>();
        this.listenersFoundRepos = new ArrayList<>();
        this.listenersRepoStatus = new ArrayList<>();
    }

    public void addWatchlistListener(PropertyChangeListener l) { listenersWatchlist.add(l); }

    public void addFoundReposListener(PropertyChangeListener l) { listenersFoundRepos.add(l); }

    public void addRepoStatusListener(PropertyChangeListener l) { listenersRepoStatus.add(l); }

    public boolean removeWatchlistListener(PropertyChangeListener l) { return listenersWatchlist.remove(l); }

    public boolean removeFoundReposListener(PropertyChangeListener l) { return listenersFoundRepos.remove(l); }

    public boolean removeRepoStatusListener(PropertyChangeListener l) { return listenersRepoStatus.remove(l); }

    private void notifyWatchlistChanged() {
        listenersWatchlist.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, getWatchlist())));
    }

    private void notifyFoundReposChanged() {
        listenersFoundRepos.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "foundRepos", null, getFoundRepos())));
    }

    private void notifyRepoStatusChanged(RepositoryInformation repo) {
        listenersRepoStatus.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "repoStatus", null, repo)));
    }

    public Set<RepositoryInformation> getWatchlist() {
        if (watchlist == null) {
             watchlist = new HashMap<>();
        }
        return new HashSet<>(watchlist.values());
    }

    public synchronized void setWatchlist(Set<RepositoryInformation> watchlist) {
        if (watchlist != null) {
            this.watchlist.clear();
            watchlist.forEach(repoInfo -> this.watchlist.put(repoInfo.getPath(), repoInfo));
        }
    }

    public Set<RepositoryInformation> getFoundRepos() {
        if (foundRepos == null) {
            foundRepos = new HashMap<>();
        }
        return new HashSet<>(foundRepos.values());
    }

    public void setFoundRepos(Set<RepositoryInformation> foundRepos) {
        if (foundRepos != null) {
            this.foundRepos.clear();
            foundRepos.forEach(repoInfo -> this.foundRepos.put(repoInfo.getPath(), repoInfo));
        }
    }

    public Set<RepositoryInformation> getAllRepos() {
        return Stream.concat(watchlist.values().stream(),
                foundRepos.values().stream()).collect(Collectors.toSet());
    }

    public boolean exists(RepositoryInformation repo) {
        return getListName(repo) != null;
    }

    public RepoList getListName(String repoURL) {
        return getListName(new RepositoryInformation(repoURL));
    }

    public RepoList getListName(RepositoryInformation repo) {
        if (watchlist.containsKey(repo.getPath())) {
            return RepoList.WATCH;
        } else if (foundRepos.containsKey(repo.getPath())) {
            return RepoList.FOUND;
        } else {
            return null;
        }
    }

    public void addToList(RepoList list, Collection<RepositoryInformation> repos) {
        checkRepoPathValidity(repos);
        switch (list) {
            case WATCH:
                repos.forEach(repoInfo -> watchlist.put(repoInfo.getPath(), repoInfo));
                notifyWatchlistChanged();
                break;
            case FOUND:
                repos.forEach(repoInfo -> foundRepos.put(repoInfo.getPath(), repoInfo));
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
                repos.forEach(repoInfo -> watchlist.remove(repoInfo.getPath()));
                notifyWatchlistChanged();
                break;
            case FOUND:
                repos.forEach(repoInfo -> foundRepos.remove(repoInfo.getPath()));
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
        for (RepositoryInformation repo : reposToCheck) {
            if (!Utils.validateRepositoryPath(repo.getPath())) {
                repo.setStatus(RepositoryInformation.RepoStatus.PATH_INVALID);
            }
        }
    }

    public RepositoryInformation getRepo(String path) {
        RepositoryInformation repo = watchlist.get(path);
        if (repo == null) {
            repo = foundRepos.get(path);
        }
        return repo;
    }

    public void updateRepoStatus(String path, RepositoryInformation.RepoStatus status) {
        RepositoryInformation repo = getRepo(path);
        if (repo == null) {
            throw new NoSuchElementException();
        }
        repo.setStatus(status);
        notifyRepoStatusChanged(repo);
    }

    public void setNewChanges(String path, int newCommitCount) {
        RepositoryInformation repo = getRepo(path);
        if (repo == null) {
            throw new NoSuchElementException();
        }
        repo.setNewChanges(newCommitCount);
        notifyRepoStatusChanged(repo);
    }

    public Set<RepositoryInformation> getList(RepoList list) {
        if (list == RepoList.FOUND)
            return getFoundRepos();
        else if (list == RepoList.WATCH)
            return getWatchlist();
        else
            return new HashSet<>();
    }

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
            repo.setAuthMethod(RepositoryInformation.AuthMethod.NONE);
        }
    }

    public enum RepoList {
        FOUND,
        WATCH,
        BLACK
    }
}
