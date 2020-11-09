package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationCellFactory;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerMain implements Initializable, ErrorDisplay, StatusDisplay, PropertyChangeListener {

    @FXML
    private ProgressIndicator indicatorScanRunning;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnCheckStatus;
    @FXML
    private ListView<RepositoryInformation> watchlist;
    private FileManager fileManager;
    private GitManager gitManager;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        fileManager = FileManager.getInstance();
        try {
            fileManager.init();
        } catch (IOException e) {
           Logger.getLogger(getClass().getName()).log(Level.SEVERE, "error occurred during file manager init", e);
           showError(ResourceStore.getResourceBundle().getString("errormsg.file_access_denied"));
        }
        fileManager.addWatchlistListener(this);
        gitManager = GitManager.getInstance();
        // check repo status
        gitManager.updateWatchlistStatusAsync((success, reposChecked, ex) -> {});
        setupUI();
    }

    private void setupUI() {
        watchlist.setCellFactory(new RepositoryInformationCellFactory(this));
        watchlist.setPlaceholder(new Label(ResourceStore.getResourceBundle().getString("list.noentries")));
        watchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setWatchlistDisplay(fileManager.getWatchlist());
        indicatorScanRunning.visibleProperty().bind(ControllerScan.scanRunningProperty());
        indicatorScanRunning.managedProperty().bind(indicatorScanRunning.visibleProperty());
    }

    @FXML
    public void btnScanClicked(ActionEvent actionEvent) {
        try {
            openScanWindow();
        }
        catch (Exception ex) {
            Logger.getAnonymousLogger().severe(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void openScanWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/reposcan/scan.fxml"),
                ResourceStore.getResourceBundle());
        Parent root = loader.load();
        ControllerScan controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getResourceBundle().getString("scanpc"));
        stage.setScene(new Scene(root));
        stage.setOnHidden(event -> controller.cleanup());
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }

    @FXML
    public void btnCheckStatusClicked(ActionEvent actionEvent) {
        displayStatus("Updating status of watchlist...");
        btnCheckStatus.setDisable(true);
        gitManager.updateWatchlistStatusAsync((success, reposChecked, ex) -> {
            displayStatus("Updated status of " + reposChecked + " repositories");
            btnCheckStatus.setDisable(false);
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        Platform.runLater(() -> {
            if (e.getPropertyName().equals("watchlist")) {
                setWatchlistDisplay((Collection<RepositoryInformation>)e.getNewValue());
            }
        });
    }

    private void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        watchlist.getItems().clear();
        watchlist.getItems().addAll(repoInfo);
        Collections.sort(watchlist.getItems());
    }

    @Override
    public void displayStatus(String status) {
        Platform.runLater(() -> lblStatus.setText(status));
    }
}
