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
    private File fileWatchlist;
    private List<PropertyChangeListener> listenersWatchlist;
    private List<RepositoryInformation> watchlist;

    public static synchronized FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }

    private FileManager() {
        this.mapper = XmlMapper.xmlBuilder().build();
        this.fileWatchlist = new File(System.getenv("APPDATA") + "/GitRepoMonitor/watchlist.xml");
        this.listenersWatchlist = new ArrayList<>();
        this.watchlist = new ArrayList<>();
    }

    public synchronized void init() throws IOException {
        try {
            watchlist = mapper.readValue(fileWatchlist, new TypeReference<List<RepositoryInformation>>(){});
        } catch (IOException e) {
            fileWatchlist.getParentFile().mkdirs();
            fileWatchlist.createNewFile();
            watchlist = new ArrayList<>();
            e.printStackTrace();
        }
    }

    public synchronized void addWatchlistListener(PropertyChangeListener l) {
        listenersWatchlist.add(l);
    }

    public synchronized void addToWatchlist(List<RepositoryInformation> repos) throws IOException {
        watchlist.addAll(repos);
        persistWatchlist();
        notifyWatchlistChanged();
    }

    public synchronized void addToWatchlist(RepositoryInformation repo) throws IOException {
        watchlist.add(repo);
        persistWatchlist();
        notifyWatchlistChanged();
    }

    private synchronized void persistWatchlist() throws IOException {
        mapper.writeValue(fileWatchlist, watchlist);
        Logger.getAnonymousLogger().info("Wrote watchlist to " + fileWatchlist.getAbsolutePath());
    }

    private void notifyWatchlistChanged() {
        // TODO: Change maybe? (Not very clean with oldValue = null)
        listenersWatchlist.stream().forEach(propertyChangeListener ->
                propertyChangeListener.propertyChange(new PropertyChangeEvent(this, "watchlist", null, watchlist)));
    }

    public List<RepositoryInformation> getWatchlist() {
        return List.copyOf(watchlist);
    }
}
