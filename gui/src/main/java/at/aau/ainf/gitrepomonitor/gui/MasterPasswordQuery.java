package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Provides functionality for inputting of the Master Password.
 */
public interface MasterPasswordQuery {
    String REGEX_PW = "^.{8,25}$";  // must be between 8 and 25 characters

    default String showMasterPasswordInputDialog(boolean requireConfirmInput) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Enter Master Password");
        ImageView icon = new ImageView(ResourceStore.getImage("icon_key.png"));
        icon.setPreserveRatio(true);
        icon.setFitHeight(50);
        dialog.setGraphic(icon);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (requireConfirmInput) {
            setupDialogConfirmInput(dialog);
        } else {
            setupDialogSingleInput(dialog);
        }

        Optional<String> input = dialog.showAndWait();
        return input.orElse(null);
    }

    default Pair<String, String> showChangeMasterPasswordInputDialog() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Change Master Password");
        ImageView icon = new ImageView(ResourceStore.getImage("icon_key.png"));
        icon.setPreserveRatio(true);
        icon.setFitHeight(50);
        dialog.setGraphic(icon);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setupChangeDialog(dialog);

        Optional<Pair<String, String>> input = dialog.showAndWait();
        return input.orElse(null);
    }

    private static  void setupChangeDialog(Dialog<Pair<String, String>> dialog) {
        dialog.setHeaderText("Enter current and new Master Password information below.");

        VBox container = new VBox();
        container.setPadding(new Insets(5));
        PasswordField pwFieldCurrent = new PasswordField();
        HBox row0 = getRow(new Label("Current Master Password: "), pwFieldCurrent);

        PasswordField pwFieldNew = new PasswordField();
        HBox row1 = getRow(new Label("New Master Password: "), pwFieldNew);

        PasswordField pwFieldConfirmNew = new PasswordField();
        HBox row2 = getRow(new Label("Confirm new Master Password: "), pwFieldConfirmNew);

        HBox row3 = new HBox();
        Label lblHint = new Label();
        lblHint.setStyle("-fx-text-fill: red;");
        row3.getChildren().add(lblHint);

        container.getChildren().addAll(row0, row1, row2, row3);
        dialog.getDialogPane().setContent(container);

        Button btnOK = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        pwFieldNew.textProperty().addListener(new ChangeListenerPassword(btnOK, lblHint, pwFieldConfirmNew));
        pwFieldConfirmNew.textProperty().addListener(new ChangeListenerPassword(btnOK, lblHint, pwFieldNew));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(pwFieldCurrent.getText(), pwFieldNew.getText());
            }
            return null;
        });
        Platform.runLater(pwFieldCurrent::requestFocus);
    }

    private static void setupDialogSingleInput(Dialog<String> dialog) {
        dialog.setHeaderText("Please enter the Master Password");

        VBox container = new VBox();
        container.setPadding(new Insets(5));
        HBox row = new HBox();
        Label lblPW = new Label("Master Password: ");
        PasswordField pwField = new PasswordField();
        row.getChildren().addAll(lblPW, pwField);
        container.getChildren().add(row);
        dialog.getDialogPane().setContent(container);

        // disable OK button if no password was input (no empty passwords allowed)
        Button btnOK = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        btnOK.setDisable(true);
        pwField.textProperty().addListener((observable, oldValue, newValue) -> btnOK.setDisable(newValue.isBlank()));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return pwField.getText();
            }
            return null;
        });
        Platform.runLater(pwField::requestFocus);
    }

    private static void setupDialogConfirmInput(Dialog<String> dialog) {
        dialog.setHeaderText("A Master Password is required for this action.\nPlease enter your new Master Password below.");

        VBox container = new VBox();
        container.setPadding(new Insets(5));
        PasswordField pwField = new PasswordField();
        HBox row1 = getRow(new Label("Master Password: "), pwField);

        PasswordField pwFieldConfirm = new PasswordField();
        HBox row2 = getRow(new Label("Confirm Master Password: "), pwFieldConfirm);

        HBox row3 = new HBox();
        Label lblHint = new Label();
        lblHint.setStyle("-fx-text-fill: red;");
        row3.getChildren().add(lblHint);

        container.getChildren().addAll(row1, row2, row3);
        dialog.getDialogPane().setContent(container);

        Button btnOK = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        pwField.textProperty().addListener(new ChangeListenerPassword(btnOK, lblHint, pwFieldConfirm));
        pwFieldConfirm.textProperty().addListener(new ChangeListenerPassword(btnOK, lblHint, pwField));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return pwField.getText();
            }
            return null;
        });
        Platform.runLater(pwField::requestFocus);
    }

    private static HBox getRow(Label lbl, TextField txtField) {
        HBox row = new HBox();
        HBox leftSide = new HBox();
        leftSide.setAlignment(Pos.CENTER_LEFT);
        leftSide.getChildren().add(lbl);
        HBox.setHgrow(leftSide, Priority.ALWAYS);
        HBox rightSide = new HBox();
        rightSide.setAlignment(Pos.CENTER_RIGHT);
        rightSide.getChildren().add(txtField);
        HBox.setHgrow(rightSide, Priority.ALWAYS);
        row.getChildren().addAll(leftSide, rightSide);
        return row;
    }

    class ChangeListenerPassword implements ChangeListener<String> {

        private Button btnOK;
        private Label lblHint;
        private PasswordField otherPwField;

        public ChangeListenerPassword(Button btnOK, Label lblHint, PasswordField otherPwField) {
            this.btnOK = btnOK;
            this.lblHint = lblHint;
            this.otherPwField = otherPwField;
        }

        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
            boolean fulfillsCriteria = Pattern.matches(REGEX_PW, newValue);
            boolean samePW = newValue.equals(otherPwField.getText());
            if (!samePW) {
                lblHint.setText("Passwords do not match");
            } else if (!fulfillsCriteria) {
                lblHint.setText("Passwords need to be between 8-25 characters");
            } else {
                lblHint.setText(null);
            }

            btnOK.setDisable(!fulfillsCriteria || !samePW);
        }
    }
}
