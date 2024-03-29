package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusBarController;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepoListCellFactory;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepoKeyPressHandler;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * Controller for repo scanner.
 */
public class ControllerScan extends StatusBarController implements Initializable, PropertyChangeListener {

    private File rootDir;
    @FXML
    private Label lblPath;
    @FXML
    private Tooltip ttPath;
    @FXML
    private Hyperlink linkWholePC;
    @FXML
    private Button btnStartScan;
    @FXML
    private Button btnCancelScan;
    @FXML
    private Button btnSelectDir;
    @FXML
    private Label lblDone;
    @FXML
    private ProgressIndicator progressSpinner;
    @FXML
    private ListView<RepositoryInformation> listFoundRepos;
    @FXML
    private ListView<RepositoryInformation> listWatchlist;
    @FXML
    private Button btnAddToWatchlist;
    @FXML
    private Button btnRemoveFromWatchlist;
    @FXML
    public CheckBox ckboxExcludeRemote;

    // custom property used by ControllerMain to determine if scan is running
    private static final SimpleBooleanProperty scanRunningProperty = new SimpleBooleanProperty(null, "scanRunning", false);
    private static RepoSearchTask searchTask;
    private FileManager fileManager;

    /**
     * Get FXML loader for this GUI component.
     * @return configured FXML loader
     */
    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerScan.class.getResource("/at/aau/ainf/gitrepomonitor/gui/reposcan/scan.fxml"),
                ResourceStore.getResourceBundle());
    }

    public static ReadOnlyBooleanProperty scanRunningProperty() {
        return scanRunningProperty;
    }

    /**
     * Stop scanning task.
     */
    public static void stopScanningProcess() {
        if (searchTask != null) {
            searchTask.cancel(true);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        super.initialize(url, resourceBundle);
        fileManager = FileManager.getInstance();
        fileManager.addWatchlistListener(this);
        fileManager.addFoundReposListener(this);
        fileManager.addRepoStatusListener(this);

        setupUI();
        setupSearchTask();
        Platform.runLater(() -> btnStartScan.requestFocus());
    }

    private void setupUI() {
        btnStartScan.managedProperty().bind(btnStartScan.visibleProperty());
        btnCancelScan.managedProperty().bind(btnCancelScan.visibleProperty());
        lblDone.managedProperty().bind(lblDone.visibleProperty());
        progressSpinner.managedProperty().bind(progressSpinner.visibleProperty());
        btnCancelScan.setVisible(false);

        // use bindings to disable button if either list is not focused or no item is selected in list
        btnAddToWatchlist.disableProperty().bind(listFoundRepos.getSelectionModel().selectedItemProperty().isNull());
        btnRemoveFromWatchlist.disableProperty().bind(listWatchlist.getSelectionModel().selectedItemProperty().isNull());

        listFoundRepos.setCellFactory(new RepoListCellFactory(this, progessMonitor));
        listFoundRepos.setPlaceholder(new Label(ResourceStore.getString("repo_list.no_entries")));
        listFoundRepos.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listFoundRepos.setOnKeyPressed(new RepoKeyPressHandler(listFoundRepos));
        listWatchlist.setCellFactory(new RepoListCellFactory(this, progessMonitor));
        listWatchlist.setPlaceholder(new Label(ResourceStore.getString("repo_list.no_entries")));
        listWatchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listWatchlist.setOnKeyPressed(new RepoKeyPressHandler(listWatchlist));
        setWatchlistDisplay(fileManager.getWatchlist());
        setFoundReposDisplay(fileManager.getFoundRepos());

        lblDone.visibleProperty().bind(scanRunningProperty().not());
    }

    /**
     * Remove all registered listeners.
     */
    public void cleanup() {
        fileManager.removeFoundReposListener(this);
        fileManager.removeWatchlistListener(this);
    }

    @FXML
    public void btnSelectDirClicked(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(ResourceStore.getString("scanpc.selectdir.title"));
        directoryChooser.setInitialDirectory(rootDir);
        File selectedDirectory = directoryChooser.showDialog(lblStatus.getScene().getWindow());
        if (selectedDirectory != null) {
            rootDir = selectedDirectory;
            lblPath.setText(selectedDirectory.getAbsolutePath());
            ttPath.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    public void linkScanWholePcClicked(ActionEvent actionEvent) {
        rootDir = null;     // means scan whole pc
        lblPath.setText(ResourceStore.getString("scanpc.wholepc"));
        ttPath.setText(ResourceStore.getString("scanpc.wholepc"));
        linkWholePC.setVisited(false);
    }

    @FXML
    public void btnStartScanClicked(ActionEvent actionEvent) {
        searchTask = new RepoSearchTask(rootDir, ckboxExcludeRemote.isSelected());
        setupSearchTask();
        setScanRunningMode(true);
        scanRunningProperty.set(true);
        new Thread(searchTask).start();
    }

    /**
     * Setup all necessary listeners and binding related to the search task in this GUI.
     * Only has an effect if searchTask != null
     */
    private void setupSearchTask() {
        if (searchTask != null) {
            setScanRunningMode(!searchTask.isStopped());
            lblStatus.textProperty().bind(searchTask.messageProperty());
            searchTask.setOnFailed(workerStateEvent -> {
                scanFinished();
                lblDone.setText(ResourceStore.getString("scanpc.status.failed"));
            });
            searchTask.setOnSucceeded(workerStateEvent -> {
                scanFinished();
                lblDone.setText(ResourceStore.getString("scanpc.status.done"));
            });
            searchTask.setOnCancelled(workerStateEvent -> {
                scanFinished();
                lblDone.setText(ResourceStore.getString("scanpc.status.cancelled"));
            });
        }
        else {
            setScanRunningMode(false);
        }
    }

    /**
     * Set scan finished
     */
    private void scanFinished() {
        setScanRunningMode(false);
        scanRunningProperty.set(false);
    }

    /**
     * Setup GUI to reflect whether or not a scan is running.
     * @param scanRunning True, if scan is running
     */
    private void setScanRunningMode(boolean scanRunning) {
        btnStartScan.setVisible(!scanRunning);
        btnCancelScan.setVisible(scanRunning);
        btnSelectDir.setDisable(scanRunning);
        linkWholePC.setDisable(scanRunning);
        ckboxExcludeRemote.setDisable(scanRunning);
        progressSpinner.setVisible(scanRunning);
    }

    @FXML
    public void btnCancelScanClicked(ActionEvent actionEvent) {
        searchTask.cancel(true);
    }

    @FXML
    public void btnAddToWatchlistClicked(ActionEvent actionEvent) {
        List<RepositoryInformation> selectedItems = List.copyOf(listFoundRepos.getSelectionModel().getSelectedItems());
        fileManager.foundToWatchlist(selectedItems);
    }

    @FXML
    public void btnRemoveFromWatchlistClicked(ActionEvent actionEvent) {
        List<RepositoryInformation> selectedItems = List.copyOf(listWatchlist.getSelectionModel().getSelectedItems());
        fileManager.watchlistToFound(selectedItems);
    }

    /**
     * Called when found repos, watchlist or repo status changes.
     * @param e Event
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        Platform.runLater(() -> {
            switch (e.getPropertyName()) {
                case "watchlist":
                    setWatchlistDisplay((Collection<RepositoryInformation>) e.getNewValue());
                    break;
                case "foundRepos":
                    setFoundReposDisplay((Collection<RepositoryInformation>) e.getNewValue());
                    break;
                case "repoStatus":
                    listWatchlist.refresh();
                    listFoundRepos.refresh();
                    break;
            }
        });
    }

    /**
     * Set items to display in watchlist.
     * @param repoInfo Repos to display.
     */
    private synchronized void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        listWatchlist.getItems().clear();
        listWatchlist.getItems().addAll(repoInfo);
        Collections.sort(listWatchlist.getItems());
    }

    /**
     * Set items to display in found repos.
     * Found repos are sorted by last modification date in descending order.
     * @param repoInfo Repos to display.
     */
    private synchronized void setFoundReposDisplay(Collection<RepositoryInformation> repoInfo) {
        listFoundRepos.getItems().clear();
        listFoundRepos.getItems().addAll(repoInfo);
        for (RepositoryInformation repo : repoInfo) {
            repo.setModifiedDate(Utils.getLastChangedDate(repo.getPath()));
        }

        listFoundRepos.getItems().sort((o1, o2) -> {
            if (o1.equals(o2)) return 0;
            int retVal = o2.getModifiedDate().compareTo(o1.getModifiedDate());
            if (retVal == 0) {
                retVal = o2.compareTo(o1);
            }
            return retVal;
        });
    }
}