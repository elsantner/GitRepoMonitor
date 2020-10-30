package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.files.FileManager;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerMain implements Initializable, PropertyChangeListener {

    private ResourceBundle localStrings;
    @FXML
    private Button btnScan;
    @FXML
    private ListView<RepositoryInformation> watchlist;
    private FileManager fileManager;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        localStrings = resourceBundle;
        fileManager = FileManager.getInstance();
        try {
            fileManager.init();
        } catch (IOException e) {
           Logger.getLogger(getClass().getName()).log(Level.SEVERE, "error occurred during file manager init", e);
           showError("Cannot access file storage");
        }
        fileManager.addWatchlistListener(this);
        setupUI();
    }

    private void setupUI() {
        watchlist.setPlaceholder(new Label("No Repos"));
        setWatchlistDisplay(fileManager.getWatchlist());
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("An error occurred");
        a.setContentText(msg);
        a.showAndWait();
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
        Parent root = FXMLLoader.load(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/reposcan/scan.fxml"), localStrings);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("Scan PC");
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals("watchlist")) {
            setWatchlistDisplay((List<RepositoryInformation>)e.getNewValue());
        }
    }

    private void setWatchlistDisplay(List<RepositoryInformation> repoInfo) {
        watchlist.getItems().clear();
        watchlist.getItems().addAll(repoInfo);
    }
}
