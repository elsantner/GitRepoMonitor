package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.files.FileManager;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.RepositoryInformationCellFactory;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import at.aau.ainf.gitrepomonitor.gui.reposcan.RepoSearchTask;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerMain implements Initializable, ErrorDisplay, PropertyChangeListener {

    @FXML
    private ProgressIndicator indicatorScanRunning;
    @FXML
    private ListView<RepositoryInformation> watchlist;
    private FileManager fileManager;

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
        setupUI();
    }

    private void setupUI() {
        watchlist.setCellFactory(new RepositoryInformationCellFactory());
        watchlist.setPlaceholder(new Label(ResourceStore.getResourceBundle().getString("list.noentries")));
        watchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setWatchlistDisplay(fileManager.getWatchlist());
        indicatorScanRunning.visibleProperty().bind(ControllerScan.scanRunningProperty());
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
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("watchlist")) {
            setWatchlistDisplay((Collection<RepositoryInformation>)e.getNewValue());
        }
    }

    private void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        watchlist.getItems().clear();
        watchlist.getItems().addAll(repoInfo);
        Collections.sort(watchlist.getItems());
    }
}
