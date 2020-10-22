package at.aau.ainf.gitrepomonitor.files;

import java.util.HashSet;
import java.util.Set;

public class RepoListWrapper {
    private Set<RepositoryInformation> watchlist;
    private Set<RepositoryInformation> foundRepos;

    public RepoListWrapper() {
        this.watchlist = new HashSet<>();
        this.foundRepos = new HashSet<>();
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
}
