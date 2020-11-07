package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.files.FileManager;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.RepositoryInformationCellFactory;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

public class ControllerScan implements Initializable, PropertyChangeListener {

    private File rootDir;
    @FXML
    private Label lblStatus;
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

    private static final SimpleBooleanProperty scanRunningProperty = new SimpleBooleanProperty(null, "scanRunning", false);
    private static RepoSearchTask searchTask;
    private FileManager fileManager;

    public static ReadOnlyBooleanProperty scanRunningProperty() {
        return scanRunningProperty;
    }

    public static void stopScanningProcess() {
        if (searchTask != null) {
            searchTask.cancel(true);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        fileManager = FileManager.getInstance();
        fileManager.addWatchlistListener(this);
        fileManager.addFoundReposListener(this);

        setupUI();
        setupSearchTask();
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

        listFoundRepos.setCellFactory(new RepositoryInformationCellFactory());
        listFoundRepos.setPlaceholder(new Label(ResourceStore.getResourceBundle().getString("list.noentries")));
        listWatchlist.setCellFactory(new RepositoryInformationCellFactory());
        listWatchlist.setPlaceholder(new Label(ResourceStore.getResourceBundle().getString("list.noentries")));
        setWatchlistDisplay(fileManager.getWatchlist());
        setFoundReposDisplay(fileManager.getFoundRepos());

        lblDone.visibleProperty().bind(scanRunningProperty().not());
    }

    public void cleanup() {
        fileManager.removeFoundReposListener(this);
        fileManager.removeWatchlistListener(this);
    }

    @FXML
    public void btnSelectDirClicked(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(ResourceStore.getResourceBundle().getString("scanpc.selectdir.title"));
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
        lblPath.setText(ResourceStore.getResourceBundle().getString("scanpc.wholepc"));
        ttPath.setText(ResourceStore.getResourceBundle().getString("scanpc.wholepc"));
        linkWholePC.setVisited(false);
    }

    @FXML
    public void btnStartScanClicked(ActionEvent actionEvent) {
        searchTask = new RepoSearchTask(rootDir);
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
                lblDone.setText(ResourceStore.getResourceBundle().getString("scanpc.status.failed"));
            });
            searchTask.setOnSucceeded(workerStateEvent -> {
                scanFinished();
                lblDone.setText(ResourceStore.getResourceBundle().getString("scanpc.status.done"));
            });
            searchTask.setOnCancelled(workerStateEvent -> {
                scanFinished();
                lblDone.setText(ResourceStore.getResourceBundle().getString("scanpc.status.cancelled"));
            });
        }
        else {
            setScanRunningMode(false);
        }
    }

    private void scanFinished() {
        setScanRunningMode(false);
        scanRunningProperty.set(false);
    }

    private void setScanRunningMode(boolean scanRunning) {
        btnStartScan.setVisible(!scanRunning);
        btnCancelScan.setVisible(scanRunning);
        btnSelectDir.setDisable(scanRunning);
        linkWholePC.setDisable(scanRunning);
        progressSpinner.setVisible(scanRunning);
    }

    @FXML
    public void btnCancelScanClicked(ActionEvent actionEvent) {
        searchTask.cancel(true);
    }

    @FXML
    public void btnAddToWatchlistClicked(ActionEvent actionEvent) {
        List<RepositoryInformation> selectedItems = List.copyOf(listFoundRepos.getSelectionModel().getSelectedItems());
        fileManager.addToWatchlist(selectedItems);
    }

    @FXML
    public void btnRemoveFromWatchlistClicked(ActionEvent actionEvent) {
        List<RepositoryInformation> selectedItems = List.copyOf(listWatchlist.getSelectionModel().getSelectedItems());
        fileManager.removeFromWatchlist(selectedItems);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("watchlist")) {
            setWatchlistDisplay((Collection<RepositoryInformation>)e.getNewValue());
        } else if (e.getPropertyName().equals("foundRepos")) {
            setFoundReposDisplay((Collection<RepositoryInformation>)e.getNewValue());
        }
    }

    private synchronized void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        listWatchlist.getItems().clear();
        listWatchlist.getItems().addAll(repoInfo);
        Collections.sort(listWatchlist.getItems());
    }

    private synchronized void setFoundReposDisplay(Collection<RepositoryInformation> repoInfo) {
        listFoundRepos.getItems().clear();
        listFoundRepos.getItems().addAll(repoInfo);
        listFoundRepos.getItems().sort((o1, o2) -> {
            if (o1.equals(o2)) return 0;
            int retVal = o2.getDateAdded().compareTo(o1.getDateAdded());
            if (retVal == 0) {
                retVal = o1.compareTo(o2);
            }
            return retVal;
        });
    }
}