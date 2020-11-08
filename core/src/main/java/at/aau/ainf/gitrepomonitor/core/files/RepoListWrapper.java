package at.aau.ainf.gitrepomonitor.core.files;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepoListWrapper {
    private Set<RepositoryInformation> watchlist;
    private Set<RepositoryInformation> foundRepos;
    private List<PropertyChangeListener> listenersWatchlist;
    private List<PropertyChangeListener> listenersFoundRepos;

    public RepoListWrapper() {
        this.watchlist = new HashSet<>();
        this.foundRepos = new HashSet<>();
        this.listenersWatchlist = new ArrayList<>();
        this.listenersFoundRepos = new ArrayList<>();
    }

    public void addWatchlistListener(PropertyChangeListener l) { listenersWatchlist.add(l); }

    public void addFoundReposListener(PropertyChangeListener l) { listenersFoundRepos.add(l); }

    public boolean removeWatchlistListener(PropertyChangeListener l) { return listenersWatchlist.remove(l); }

    public boolean removeFoundReposListener(PropertyChangeListener l) { return listenersFoundRepos.remove(l); }

    private void notifyWatchlistChanged() {
        listenersWatchlist.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, getWatchlist())));
    }

    private void notifyFoundReposChanged() {
        listenersFoundRepos.forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "foundRepos", null, getFoundRepos())));
    }

    public Set<RepositoryInformation> getWatchlist() {
        if (watchlist == null) {
             watchlist = new HashSet<>();
        }
        return watchlist;
    }

    public void setWatchlist(Set<RepositoryInformation> watchlist) {
        this.watchlist = watchlist;
    }

    public Set<RepositoryInformation> getFoundRepos() {
        if (foundRepos == null) {
            foundRepos = new HashSet<>();
        }
        return foundRepos;
    }

    public void setFoundRepos(Set<RepositoryInformation> foundRepos) {
        this.foundRepos = foundRepos;
    }

    public boolean exists(RepositoryInformation repo) {
        return getListName(repo) != null;
    }

    public RepoList getListName(String repoURL) {
        return getListName(new RepositoryInformation(repoURL));
    }

    public RepoList getListName(RepositoryInformation repo) {
        if (watchlist.contains(repo)) {
            return RepoList.WATCH;
        } else if (foundRepos.contains(repo)) {
            return RepoList.FOUND;
        } else {
            return null;
        }
    }

    public void addToList(RepoList list, Collection<RepositoryInformation> repos) {
        checkRepoPathValidity(repos);
        switch (list) {
            case WATCH:
                watchlist.addAll(repos);
                notifyWatchlistChanged();
                break;
            case FOUND:
                foundRepos.addAll(repos);
                notifyFoundReposChanged();
                break;
        }
    }

    public void addToList(RepoList list, RepositoryInformation repo) {
        addToList(list, Collections.singletonList(repo));
    }

    public boolean removeFromList(RepoList list, Collection<RepositoryInformation> repos) {
        boolean status = false;
        switch (list) {
            case WATCH:
                status = watchlist.removeAll(repos);
                notifyWatchlistChanged();
                break;
            case FOUND:
                status = foundRepos.removeAll(repos);
                notifyFoundReposChanged();
                break;
        }
        return status;
    }

    public boolean removeFromList(RepoList list, RepositoryInformation repo) {
        return removeFromList(list, Collections.singletonList(repo));
    }

    /**
     * Sets pathValid property of all repos
     */
    public void checkRepoPathValidity() {
        for (RepositoryInformation repo : Stream.concat(watchlist.stream(), foundRepos.stream()).collect(Collectors.toList())) {
            repo.setPathValid(GitRepoHelper.validateRepositoryPath(repo.getPath()));
        }
    }

    private void checkRepoPathValidity(Collection<RepositoryInformation> reposToCheck) {
        for (RepositoryInformation repo : reposToCheck) {
            repo.setPathValid(GitRepoHelper.validateRepositoryPath(repo.getPath()));
        }
    }

    public enum RepoList {
        FOUND,
        WATCH,
        BLACK
    }
}
