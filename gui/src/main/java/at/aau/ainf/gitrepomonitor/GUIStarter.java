package at.aau.ainf.gitrepomonitor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class GUIStarter extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        ResourceBundle languageResource =
                ResourceBundle.getBundle("at.aau.ainf.gitrepomonitor.localization.lang", Locale.ENGLISH);

        Parent root = FXMLLoader.load(getClass().getResource(
                "/at/aau/ainf/gitrepomonitor/gui/main/main.fxml"), languageResource);
        primaryStage.setTitle("Git Repository Monitor");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
