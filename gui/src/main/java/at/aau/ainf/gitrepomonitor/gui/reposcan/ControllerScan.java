package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.files.FileManager;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ControllerScan implements Initializable, PropertyChangeListener {

    private ResourceBundle localStrings;
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

    private ListProperty<RepositoryInformation> listProperty = new SimpleListProperty<>();
    private RepoSearchTask searchTask;
    private FileManager fileManager;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        localStrings = resourceBundle;
        btnStartScan.managedProperty().bind(btnStartScan.visibleProperty());
        btnCancelScan.managedProperty().bind(btnCancelScan.visibleProperty());
        lblDone.managedProperty().bind(lblDone.visibleProperty());
        progressSpinner.managedProperty().bind(progressSpinner.visibleProperty());
        btnCancelScan.setVisible(false);

        // use bindings to disable button if either list is not focused or no item is selected in list
        btnAddToWatchlist.disableProperty().bind(listFoundRepos.getSelectionModel().selectedItemProperty().isNull());
        btnRemoveFromWatchlist.disableProperty().bind(listWatchlist.focusedProperty().not()
                .or(listWatchlist.getSelectionModel().selectedItemProperty().isNull()));

        listFoundRepos.setPlaceholder(new Label("No Repos"));
        listWatchlist.setPlaceholder(new Label("No Repos"));

        fileManager = FileManager.getInstance();
        fileManager.addWatchlistListener(this);
    }

    @FXML
    public void btnSelectDirClicked(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(localStrings.getString("scanpc.selectdir.title"));
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
        lblPath.setText("<Whole PC>");
        ttPath.setText("<Whole PC>");
        linkWholePC.setVisited(false);
    }

    @FXML
    public void btnStartScanClicked(ActionEvent actionEvent) {
        setScanRunningMode(true);
        searchTask = new RepoSearchTask(rootDir);
        lblStatus.textProperty().bind(searchTask.messageProperty());
        searchTask.setOnFailed(workerStateEvent -> {
            setScanRunningMode(false);
            lblStatus.textProperty().unbind();
        });
        searchTask.setOnSucceeded(workerStateEvent -> {
            setScanRunningMode(false);
            lblStatus.textProperty().unbind();
        });
        listProperty.set(searchTask.getFoundRepos());
        listFoundRepos.itemsProperty().bind(listProperty);
        new Thread(searchTask).start();
    }

    private void setScanRunningMode(boolean scanRunning) {
        btnStartScan.setVisible(!scanRunning);
        btnCancelScan.setVisible(scanRunning);
        btnSelectDir.setDisable(scanRunning);
        linkWholePC.setDisable(scanRunning);
        progressSpinner.setVisible(scanRunning);
        lblDone.setVisible(!scanRunning);
    }

    @FXML
    public void btnCancelScanClicked(ActionEvent actionEvent) {
        setScanRunningMode(false);
        searchTask.cancel();
    }

    @FXML
    public void btnAddToWatchlistClicked(ActionEvent actionEvent) {
        try {
            List<RepositoryInformation> selectedItems = List.copyOf(listFoundRepos.getSelectionModel().getSelectedItems());
            fileManager.addToWatchlist(selectedItems);
        } catch (IOException e) {
            e.printStackTrace();
            lblStatus.setText(e.getClass().getCanonicalName() + ": " + e.getMessage());
        }
        /*listWatchlist.getItems().addAll(selectedItems);
        listFoundRepos.getItems().removeAll(selectedItems);*/
    }

    @FXML
    public void btnRemoveFromWatchlistClicked(ActionEvent actionEvent) {

    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("watchlist")) {
            listWatchlist.getItems().clear();
            listWatchlist.getItems().addAll((List<RepositoryInformation>)e.getNewValue());
        }
    }
}
