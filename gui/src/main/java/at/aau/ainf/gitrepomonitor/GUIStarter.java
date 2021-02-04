package at.aau.ainf.gitrepomonitor;

import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.main.ControllerMain;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Main JavaFX application which starts the main GUI
 */
public class GUIStarter extends Application {

    private Stage primaryStage;

    /**
     * Create and load main GUI.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        // set resource bundle for easy access
        ResourceStore.setResourceBundle(
                ResourceBundle.getBundle("at.aau.ainf.gitrepomonitor.localization.lang", Locale.ENGLISH));

        FXMLLoader loader = ControllerMain.getLoader();
        Parent root = loader.load();
        ((ControllerMain)loader.getController()).setStage(primaryStage);

        primaryStage.setTitle(ResourceStore.getString("app_title"));
        primaryStage.getIcons().add(ResourceStore.getImage("icon_app.png"));
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.setOnCloseRequest(confirmCloseEventHandler);
        primaryStage.show();
        primaryStage.setMinWidth(primaryStage.getWidth());
        primaryStage.setMinHeight(primaryStage.getHeight());
    }

    /**
     * Event interceptor for showing a confirmation dialog if user attempts to close application
     * while a repo scan is still running.
     */
    private final EventHandler<WindowEvent> confirmCloseEventHandler = event -> {
        if (ControllerScan.scanRunningProperty().get()) {
            Alert closeConfirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    ResourceStore.getString("exit_dialog.text")
            );

            Button exitButton = (Button) closeConfirmation.getDialogPane().lookupButton(
                    ButtonType.OK
            );
            exitButton.setText(ResourceStore.getString("btn.exit"));
            closeConfirmation.setHeaderText(ResourceStore.getString("exit_dialog.title"));
            closeConfirmation.initModality(Modality.APPLICATION_MODAL);
            closeConfirmation.initOwner(primaryStage);

            Optional<ButtonType> closeResponse = closeConfirmation.showAndWait();
            // if not confirmed, abort closing of program
            if (closeResponse.isEmpty() || !ButtonType.OK.equals(closeResponse.get())) {
                event.consume();
            } else {
                // if confirmed, stop scan and stop all secure storage processes.
                ControllerScan.stopScanningProcess();
                SecureStorage.getImplementation().cleanup();
            }
        } else {
            SecureStorage.getImplementation().cleanup();
        }
    };

    public static void main(String[] args) {
        launch(args);
    }
}
