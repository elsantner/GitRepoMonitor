package at.aau.ainf.gitrepomonitor.gui.main;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class ControllerMain implements Initializable {

    private ResourceBundle localStrings;
    @FXML
    private Button btnScan;
    @FXML
    private ListView watchlist;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        localStrings = resourceBundle;
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
        Parent root = FXMLLoader.load(getClass().getResource("../reposcan/scan.fxml"), localStrings);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("ABC");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
