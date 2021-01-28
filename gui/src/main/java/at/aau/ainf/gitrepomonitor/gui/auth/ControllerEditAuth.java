package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SSLInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ControllerEditAuth implements Initializable, AlertDisplay, MasterPasswordQuery {

    @FXML
    public TextField txtHttpsUsername;
    @FXML
    public ToggleButton toggleShowPW;
    @FXML
    public PasswordField txtHttpsPasswordHidden;
    @FXML
    public TextField txtHttpsPasswordShown;
    @FXML
    public AnchorPane authContainerHTTPS;
    @FXML
    public AnchorPane authContainerSSL;
    @FXML
    public ImageView iconShowPW;
    @FXML
    public Tooltip ttShowPW;
    @FXML
    public Button btnLoadCredentials;
    @FXML
    public TextField txtSslKeyPath;
    @FXML
    public Button btnChooseSslKeyPath;
    @FXML
    public PasswordField txtSslPassphraseHidden;
    @FXML
    public TextField txtSslPassphraseShown;
    @FXML
    public ToggleButton toggleShowSslPassphrase;
    @FXML
    public ImageView iconShowSslPassphrase;
    @FXML
    public Tooltip ttShowSslPassphrase;
    @FXML
    private TextField txtName;

    private SecureStorage secureStorage;
    private AuthenticationInformation authInfo = null;
    private boolean createNew = false;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerEditAuth.class.getResource("/at/aau/ainf/gitrepomonitor/gui/auth/edit_auth.fxml"),
                ResourceStore.getResourceBundle());
    }

    public static void openWindow(AuthenticationInformation authInfo) throws IOException {
        FXMLLoader loader = ControllerEditAuth.getLoader();
        Parent root = loader.load();
        if (((ControllerEditAuth)loader.getController()).setAuthInfo(authInfo)) {     // set repo information to display
            configureStage(root);
        }
    }

    public static void openWindowNewAuth(RepositoryInformation.AuthMethod authMethod) throws IOException {
        FXMLLoader loader = ControllerEditAuth.getLoader();
        Parent root = loader.load();
        ((ControllerEditAuth)loader.getController()).setCreateNew(authMethod);     // create new auth
        configureStage(root);
    }

    private static void configureStage(Parent root) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("edit_auth"));
        stage.getIcons().add(ResourceStore.getImage("icon_app.png"));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.secureStorage = SecureStorage.getImplementation();
        setupUI();
    }

    private boolean attemptLoadCredentials() {
        try {
            String masterPW = getMasterPasswordIfRequired();
            loadCredentials(masterPW);
            return true;
        } catch (SecurityException ex) {
            // nothing, method is aborted
            return false;
        } catch (AuthenticationException ex) {
            showErrorWrongMasterPW();
            return false;
        } catch (Exception e) {
            showError(e.getMessage());
            return false;
        }
    }

    private void setupUI() {
        txtName.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(!newValue) { // when losing focus, check validity of input
                validateTextFieldName();
            }
        });
        // only show selected credential input
        authContainerHTTPS.managedProperty().bind(authContainerHTTPS.visibleProperty());
        authContainerSSL.managedProperty().bind(authContainerSSL.visibleProperty());

        // sync text between hidden and shown password fields
        txtHttpsPasswordHidden.textProperty().bindBidirectional(txtHttpsPasswordShown.textProperty());
        txtHttpsPasswordHidden.visibleProperty().bind(toggleShowPW.selectedProperty().not());
        txtHttpsPasswordShown.visibleProperty().bind(toggleShowPW.selectedProperty());
        toggleShowPW.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                iconShowPW.setImage(ResourceStore.getImage("icon_hidden.png"));
                ttShowPW.setText(ResourceStore.getString("edit_repo.hide_password"));
            } else {
                iconShowPW.setImage(ResourceStore.getImage("icon_visible.png"));
                ttShowPW.setText(ResourceStore.getString("edit_repo.show_password"));
            }
        });

        // sync text between hidden and shown password fields
        txtSslPassphraseHidden.textProperty().bindBidirectional(txtSslPassphraseShown.textProperty());
        txtSslPassphraseHidden.visibleProperty().bind(toggleShowSslPassphrase.selectedProperty().not());
        txtSslPassphraseShown.visibleProperty().bind(toggleShowSslPassphrase.selectedProperty());
        toggleShowSslPassphrase.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                iconShowSslPassphrase.setImage(ResourceStore.getImage("icon_hidden.png"));
                ttShowSslPassphrase.setText(ResourceStore.getString("edit_repo.hide_password"));
            } else {
                iconShowSslPassphrase.setImage(ResourceStore.getImage("icon_visible.png"));
                ttShowSslPassphrase.setText(ResourceStore.getString("edit_repo.show_password"));
            }
        });
    }

    private boolean validateTextFieldName() {
        boolean status;
        if (status = !txtName.getText().isBlank()) {
            // remove all occurrences of the style class (might be added multiple times due to timing of focus loss)
            txtName.getStyleClass().removeAll("error-input");
        } else {
            txtName.getStyleClass().add("error-input");
        }
        return status;
    }

    public boolean setAuthInfo(AuthenticationInformation authInfo) {
        this.authInfo = authInfo;

        if (authInfo != null) {
            // if credentials could not be loaded
            if (!attemptLoadCredentials()) {
                // used to abort opening of window
                return false;
            } else {
                updateAuthDisplay(authInfo);
            }
            createNew = false;
            setAuthMethodDisplay(authInfo.getAuthMethod());
        }
        return true;
    }

    public void setCreateNew(RepositoryInformation.AuthMethod authMethod) {
        if (authMethod == RepositoryInformation.AuthMethod.HTTPS) {
            authInfo = new HttpsCredentials();
        } else {
            authInfo = new SSLInformation();
        }
        createNew = true;
        setAuthMethodDisplay(authMethod);
    }

    private void setAuthMethodDisplay(RepositoryInformation.AuthMethod authMethod) {
        if (authMethod == RepositoryInformation.AuthMethod.HTTPS) {
            authContainerHTTPS.setVisible(true);
            authContainerSSL.setVisible(false);
        } else {
            authContainerHTTPS.setVisible(false);
            authContainerSSL.setVisible(true);
        }
    }

    /**
     * Update the displayed information.
     * @param authInfo Auth information to be displayed.
     */
    private void updateAuthDisplay(AuthenticationInformation authInfo) {
        this.txtName.setText(authInfo.getName());
    }

    @FXML
    public void onBtnCancelClick(ActionEvent actionEvent) {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.setMinHeight(stage.getHeight());
        stage.close();
    }

    @FXML
    public void onBtnSaveClick(ActionEvent actionEvent) {
        try {
            if (!validateTextFieldName()) {
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.empty_name"));
            }
            if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
                validateHttpsInformation();
            } else if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.SSL) {
                validateSslInformation();
            }

            if (!createNew) {
                updateAuthInfo();
                authInfo.destroy();
            } else {
                createAuthInfo();
            }

            Stage stage = (Stage) txtName.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            // nothing, method is aborted
        } catch (AuthenticationException ex) {
            showErrorWrongMasterPW();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private String getMasterPasswordIfRequired() throws AuthenticationException, IOException {
        String masterPW = null;
        if (secureStorage.isMasterPasswordSet()) {
            if (!secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
                // if master password dialog was aborted, abort method
                if (masterPW == null) {
                    throw new SecurityException(ResourceStore.getString("errormsg.mp_dialog_aborted"));
                }
            }
        } else {
            masterPW = showMasterPasswordInputDialog(true);
            if (masterPW != null) {
                secureStorage.setMasterPassword(Utils.toCharOrNull(masterPW));
            } else {
                // if master password dialog was aborted, abort method
                throw new SecurityException(ResourceStore.getString("errormsg.mp_dialog_aborted"));
            }
        }
        return masterPW;
    }

    private void createAuthInfo() throws IOException, AuthenticationException {
        // master password is required whenever auth information is changed
        String masterPW = getMasterPasswordIfRequired();

        if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
            HttpsCredentials httpsCredentials = new HttpsCredentials(txtHttpsUsername.getText(),
                    Utils.toCharOrNull(txtHttpsPasswordHidden.getText()));
            httpsCredentials.setID(authInfo.getID());
            httpsCredentials.setName(txtName.getText());
            secureStorage.store(Utils.toCharOrNull(masterPW), httpsCredentials);
            httpsCredentials.destroy();
        }
        else if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.SSL) {
            SSLInformation sslInformation = new SSLInformation(txtSslKeyPath.getText(),
                    Utils.toBytesOrNull(txtSslPassphraseHidden.getText()));
            sslInformation.setID(authInfo.getID());
            sslInformation.setName(txtName.getText());
            secureStorage.store(Utils.toCharOrNull(masterPW), sslInformation);
            sslInformation.destroy();
        }
    }

    private void updateAuthInfo() throws IOException, AuthenticationException {
        // master password is required whenever auth information is changed
        String masterPW = getMasterPasswordIfRequired();

        if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
            HttpsCredentials httpsCredentials = new HttpsCredentials(txtHttpsUsername.getText(),
                    Utils.toCharOrNull(txtHttpsPasswordHidden.getText()));
            httpsCredentials.setID(authInfo.getID());
            httpsCredentials.setName(txtName.getText());
            secureStorage.update(Utils.toCharOrNull(masterPW), httpsCredentials);
            httpsCredentials.destroy();
        }
        else if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.SSL) {
            SSLInformation sslInformation = new SSLInformation(txtSslKeyPath.getText(),
                    Utils.toBytesOrNull(txtSslPassphraseHidden.getText()));
            sslInformation.setID(authInfo.getID());
            sslInformation.setName(txtName.getText());
            secureStorage.update(Utils.toCharOrNull(masterPW), sslInformation);
            sslInformation.destroy();
        }
    }

    private void validateHttpsInformation() {
        if (authContainerHTTPS.isVisible()) {
            if (txtHttpsUsername.getText().isBlank() && txtHttpsPasswordHidden.getText().isBlank()) {
                txtHttpsUsername.getStyleClass().add("error-input");
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.https_no_credentials"));
            } else {
                txtHttpsUsername.getStyleClass().remove("error-input");
            }

            if (txtHttpsUsername.getText().isBlank() && !txtHttpsPasswordHidden.getText().isBlank()) {
                txtHttpsPasswordHidden.getStyleClass().add("error-input");
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.https_password_no_username"));
            } else {
                txtHttpsPasswordHidden.getStyleClass().remove("error-input");
            }
        }
    }

    private void validateSslInformation() {
        if (authContainerSSL.isVisible()) {
            if (txtSslKeyPath.getText().isBlank() && !txtSslPassphraseHidden.getText().isBlank()) {
                txtSslPassphraseHidden.getStyleClass().add("error-input");
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.ssl_passphrase_no_path"));
            } else {
                txtSslPassphraseHidden.getStyleClass().remove("error-input");
            }

            File keyFile = new File(txtSslKeyPath.getText());
            if (!keyFile.exists()) {
                txtSslKeyPath.getStyleClass().add("error-input");
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.ssl_invalid_key_file"));
            } else {
                txtSslKeyPath.getStyleClass().remove("error-input");
            }
        }
    }

    private void loadCredentials(String masterPW) throws AuthenticationException {
        this.authInfo = secureStorage.get(Utils.toCharOrNull(masterPW), this.authInfo.getID());

        if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
            authContainerHTTPS.setVisible(true);
            authContainerSSL.setVisible(false);
            txtHttpsUsername.setText(((HttpsCredentials)authInfo).getUsername());
            txtHttpsPasswordHidden.setText(new String(((HttpsCredentials)authInfo).getPassword()));
        }
        else if (authInfo.getAuthMethod() == RepositoryInformation.AuthMethod.SSL) {
            authContainerHTTPS.setVisible(false);
            authContainerSSL.setVisible(true);
            txtSslKeyPath.setText(((SSLInformation)authInfo).getSslKeyPath());
            txtSslPassphraseHidden.setText(new String(((SSLInformation)authInfo).getSslPassphrase()));
        }
    }

    public void onBtnChooseSslKeyPathClick(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(ResourceStore.getString("edit_repo.selectdir.title"));
        fileChooser.setInitialDirectory(Utils.getDeepestExistingDirectory(txtSslKeyPath.getText()));
        File selectedFile = fileChooser.showOpenDialog(txtName.getScene().getWindow());
        if (selectedFile != null) {
            txtSslKeyPath.setText(selectedFile.getAbsolutePath());
        }
    }
}
