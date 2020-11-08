package at.aau.ainf.gitrepomonitor;

import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class GUIStarter extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        ResourceStore.setResourceBundle(
                ResourceBundle.getBundle("at.aau.ainf.gitrepomonitor.localization.lang", Locale.ENGLISH));

        Parent root = FXMLLoader.load(getClass().getResource(
                "/at/aau/ainf/gitrepomonitor/gui/main/main.fxml"), ResourceStore.getResourceBundle());
        primaryStage.setTitle("Git Repository Monitor");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.setOnCloseRequest(confirmCloseEventHandler);
        primaryStage.show();
        primaryStage.setMinWidth(primaryStage.getWidth());
        primaryStage.setMinHeight(primaryStage.getHeight());
    }

    private final EventHandler<WindowEvent> confirmCloseEventHandler = event -> {
        if (ControllerScan.scanRunningProperty().get()) {
            Alert closeConfirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    ResourceStore.getResourceBundle().getString("exit_dialog.text")
            );

            Button exitButton = (Button) closeConfirmation.getDialogPane().lookupButton(
                    ButtonType.OK
            );
            exitButton.setText(ResourceStore.getResourceBundle().getString("btn.exit"));
            closeConfirmation.setHeaderText(ResourceStore.getResourceBundle().getString("exit_dialog.title"));
            closeConfirmation.initModality(Modality.APPLICATION_MODAL);
            closeConfirmation.initOwner(primaryStage);

            Optional<ButtonType> closeResponse = closeConfirmation.showAndWait();
            if (!ButtonType.OK.equals(closeResponse.get())) {
                event.consume();
            } else {
                ControllerScan.stopScanningProcess();
            }
        }
    };

    public static void main(String[] args) {
        launch(args);
    }
}
