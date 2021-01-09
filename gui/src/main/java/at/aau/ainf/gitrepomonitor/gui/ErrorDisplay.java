package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public interface ErrorDisplay {
    default void showError(String msg) {
        showError(ResourceStore.getString("errordialog.header"), msg);
    }

    default void showError(String header, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(ResourceStore.getString("errordialog.title"));
            ((Stage) a.getDialogPane().getScene().getWindow()).getIcons().add(ResourceStore.getImage("icon_app.png"));
            a.setHeaderText(header);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    default void showWarning(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING);
            ((Stage) a.getDialogPane().getScene().getWindow()).getIcons().add(ResourceStore.getImage("icon_app.png"));
            a.setTitle(ResourceStore.getString("warndialog.title"));
            a.setContentText(msg);
            a.showAndWait();
        });
    }
}
