package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public interface ErrorDisplay {
    default void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(ResourceStore.getResourceBundle().getString("errordialog.title"));
            a.setHeaderText(ResourceStore.getResourceBundle().getString("errordialog.header"));
            a.setContentText(msg);
            a.showAndWait();
        });
    }
}
