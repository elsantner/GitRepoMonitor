package at.aau.ainf.gitrepomonitor.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

public class LoginDialog extends Dialog<Pair<Pair<String, String>, Boolean>> {
    private TextField txtUsername;
    private PasswordField txtPassword;
    private ButtonType loginButtonType;

    public LoginDialog(String repoName) {
        super();
        setTitle("HTTPS credentials");
        setHeaderText("Input your credentials to the remote for '" + repoName + "'");

        loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        setupInputElements();

        setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(new Pair<>(txtUsername.getText(), txtPassword.getText()), false);
            }
            return null;
        });
    }

    private void setupInputElements() {
        // Create the username and password labels and fields.
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setPadding(new Insets(20, 150, 10, 10));

        txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtPassword = new PasswordField();
        txtPassword.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(txtUsername, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(txtPassword, 1, 1);

        Node btnLogin = getDialogPane().lookupButton(loginButtonType);
        btnLogin.setDisable(true);
        txtUsername.textProperty().addListener((observable, oldValue, newValue) -> {
            btnLogin.setDisable(newValue.trim().isEmpty());
        });

        getDialogPane().setContent(grid);
        Platform.runLater(() -> txtUsername.requestFocus());
    }
}
