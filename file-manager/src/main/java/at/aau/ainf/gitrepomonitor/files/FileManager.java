package at.aau.ainf.gitrepomonitor.files;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FileManager {
    private static FileManager instance;

    private XmlMapper mapper;
    private File fileRepoLists;
    private List<PropertyChangeListener> listenersWatchlist;
    private List<PropertyChangeListener> listenersFoundRepos;
    private RepoListWrapper repoListWrapper;

    public static synchronized FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }

    private FileManager() {
        this.mapper = XmlMapper.xmlBuilder().build();
        this.fileRepoLists = new File(System.getenv("APPDATA") + "/GitRepoMonitor/repolists.xml");
        this.listenersWatchlist = new ArrayList<>();
        this.listenersFoundRepos = new ArrayList<>();
    }

    public synchronized void init() throws IOException {
        try {
            repoListWrapper = mapper.readValue(fileRepoLists, new TypeReference<RepoListWrapper>(){});
        } catch (IOException e) {
            fileRepoLists.getParentFile().mkdirs();
            fileRepoLists.createNewFile();
            repoListWrapper = new RepoListWrapper();
            persistRepoLists();
            e.printStackTrace();
        }
    }

    public synchronized void addWatchlistListener(PropertyChangeListener l) {
        listenersWatchlist.add(l);
    }

    public synchronized void addFoundReposListener(PropertyChangeListener l) {
        listenersFoundRepos.add(l);
    }

    public synchronized void addToFoundRepos(RepositoryInformation repo) throws IOException {
        if (!repoListWrapper.getWatchlist().contains(repo)) {
            repoListWrapper.getFoundRepos().add(repo);
            persistRepoLists();
            notifyFoundReposChanged();
        }
    }

    public synchronized void addToWatchlist(List<RepositoryInformation> repos) throws IOException {
        repoListWrapper.getWatchlist().addAll(repos);
        repoListWrapper.getFoundRepos().removeAll(repos);
        persistRepoLists();
        notifyWatchlistChanged();
        notifyFoundReposChanged();
    }

    public synchronized void addToWatchlist(RepositoryInformation repo) throws IOException {
        repoListWrapper.getWatchlist().add(repo);
        repoListWrapper.getFoundRepos().remove(repo);
        persistRepoLists();
        notifyWatchlistChanged();
        notifyFoundReposChanged();
    }

    public synchronized void removeFromWatchlist(List<RepositoryInformation> repos) throws IOException {
        repoListWrapper.getWatchlist().removeAll(repos);
        repoListWrapper.getFoundRepos().addAll(repos);
        persistRepoLists();
        notifyWatchlistChanged();
        notifyFoundReposChanged();
    }

    public synchronized void removeFromWatchlist(RepositoryInformation repo) throws IOException {
        repoListWrapper.getWatchlist().remove(repo);
        repoListWrapper.getFoundRepos().add(repo);
        persistRepoLists();
        notifyWatchlistChanged();
        notifyFoundReposChanged();
    }

    private synchronized void persistRepoLists() throws IOException {
        mapper.writeValue(fileRepoLists, repoListWrapper);
        Logger.getAnonymousLogger().info("Wrote repolists to " + fileRepoLists.getAbsolutePath());
    }

    private void notifyWatchlistChanged() {
        // TODO: Change maybe? (Not very clean with oldValue = null)
        listenersWatchlist.stream().forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, getWatchlist())));
    }

    private void notifyFoundReposChanged() {
        // TODO: Change maybe? (Not very clean with oldValue = null)
        listenersFoundRepos.stream().forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "foundRepos", null, getFoundRepos())));
    }

    public List<RepositoryInformation> getWatchlist() {
        return List.copyOf(repoListWrapper.getWatchlist());
    }

    public List<RepositoryInformation> getFoundRepos() {
        return List.copyOf(repoListWrapper.getFoundRepos());
    }
}
