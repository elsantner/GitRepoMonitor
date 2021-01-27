package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Optional;

public interface AlertDisplay {
    default void showError(String msg) {
        showError(ResourceStore.getString("errordialog.header"), msg);
    }

    default void showError(String header, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            setIcon(a);
            a.setTitle(ResourceStore.getString("errordialog.title"));
            a.setHeaderText(header);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    default void showErrorWrongMasterPW() {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            setIcon(a);
            a.setTitle(ResourceStore.getString("status.wrong_master_password"));
            a.setHeaderText(ResourceStore.getString("status.wrong_master_password.header"));
            a.showAndWait();
        });
    }

    default void showWarning(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.WARNING);
            setIcon(a);
            a.setTitle(ResourceStore.getString("warndialog.title"));
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    default boolean showConfirmationDialog(Alert.AlertType type, String title, String header, String content) {
        Alert a = new Alert(type);
        setIcon(a);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        // add OK and CANCEL buttons independent of AlertType
        a.getButtonTypes().clear();
        a.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    default void showInformationDialog(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            setIcon(a);
            a.setTitle(title);
            a.setHeaderText(header);
            a.setContentText(content);
            a.showAndWait();
        });
    }

    private void setIcon(Alert a) {
        ((Stage) a.getDialogPane().getScene().getWindow()).getIcons().add(ResourceStore.getImage("icon_app.png"));
    }
}
