package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.SslCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.SecureStorage;
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

/**
 * Controller for edit auth credentials window.
 */
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
    private AuthenticationCredentials authInfo = null;
    private boolean createNew = false;

    /**
     * Get FXML loader for this GUI component.
     * @return configured FXML loader
     */
    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerEditAuth.class.getResource("/at/aau/ainf/gitrepomonitor/gui/auth/edit_auth.fxml"),
                ResourceStore.getResourceBundle());
    }

    /**
     * Open edit window for provided auth credentials
     * @param authInfo Auth credentials to edit
     * @throws IOException
     */
    public static void openWindow(AuthenticationCredentials authInfo) throws IOException {
        FXMLLoader loader = ControllerEditAuth.getLoader();
        Parent root = loader.load();
        if (((ControllerEditAuth)loader.getController()).setAuthInfo(authInfo)) {     // set repo information to display
            configureStage(root);
        }
    }

    /**
     * Open edit window for new auth credentials
     * @param authMethod Type of new auth credentials (HTTPS or SSL)
     * @throws IOException
     */
    public static void openWindowNewAuth(RepositoryInformation.AuthMethod authMethod) throws IOException {
        FXMLLoader loader = ControllerEditAuth.getLoader();
        Parent root = loader.load();
        ((ControllerEditAuth)loader.getController()).setCreateNew(authMethod);     // create new auth
        configureStage(root);
    }

    /**
     * Setup and show stage for window.
     * @param root Parent root
     */
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

    /**
     * Validate name input and set error style.
     * Must not be blank.
     * @return True, iff name input is valid.
     */
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

    /**
     * Set and load auth info to edit.
     * @param authInfo Auth info to display
     * @return True, iff load was successful
     */
    public boolean setAuthInfo(AuthenticationCredentials authInfo) {
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

    /**
     * Attempt to load credentials for {@code this.authInfo}.
     * This method may invoke an user input dialog if required.
     * @return True, iff load was successful
     */
    private boolean attemptLoadCredentials() {
        try {
            String masterPW = getMasterPasswordIfRequired();
            loadCredentials(masterPW);
            return true;
        } catch (SecurityException ex) {
            ex.printStackTrace();
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

    /**
     * Setup GUI for creating a new auth credentials entry.
     * @param authMethod Type of new credentials
     */
    public void setCreateNew(RepositoryInformation.AuthMethod authMethod) {
        if (authMethod == RepositoryInformation.AuthMethod.HTTPS) {
            authInfo = new HttpsCredentials();
        } else {
            authInfo = new SslCredentials();
        }
        createNew = true;
        setAuthMethodDisplay(authMethod);
    }

    /**
     * Setup GUI for specified auth method.
     * @param authMethod Type of auth method.
     */
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
    private void updateAuthDisplay(AuthenticationCredentials authInfo) {
        this.txtName.setText(authInfo.getName());
    }

    @FXML
    public void onBtnCancelClick(ActionEvent actionEvent) {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.setMinHeight(stage.getHeight());
        stage.close();
    }

    /**
     * Save current input persistently to secure storage.
     * @param actionEvent Event
     */
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
                updateAuthCredentials();
                authInfo.destroy();
            } else {
                createAuthCredentials();
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

    /**
     * Get the master password input by the user.
     * If no MP was set yet, prompt user to set one.
     * @return Master password input by user
     * @throws AuthenticationException
     * @throws IOException
     */
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

    /**
     * Create new auth credentials form current input values.
     * @throws IOException
     * @throws AuthenticationException
     */
    private void createAuthCredentials() throws IOException, AuthenticationException {
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
            SslCredentials sslCredentials = new SslCredentials(txtSslKeyPath.getText(),
                    Utils.toBytesOrNull(txtSslPassphraseHidden.getText()));
            sslCredentials.setID(authInfo.getID());
            sslCredentials.setName(txtName.getText());
            secureStorage.store(Utils.toCharOrNull(masterPW), sslCredentials);
            sslCredentials.destroy();
        }
    }

    /**
     * Update current auth credentials with the current input values.
     * @throws IOException
     * @throws AuthenticationException
     */
    private void updateAuthCredentials() throws IOException, AuthenticationException {
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
            SslCredentials sslCredentials = new SslCredentials(txtSslKeyPath.getText(),
                    Utils.toBytesOrNull(txtSslPassphraseHidden.getText()));
            sslCredentials.setID(authInfo.getID());
            sslCredentials.setName(txtName.getText());
            secureStorage.update(Utils.toCharOrNull(masterPW), sslCredentials);
            sslCredentials.destroy();
        }
    }

    /**
     * Validate HTTPS input and set error styles.
     * Username and Password must not be blank.
     * Password requires a Username.
     */
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

    /**
     * Validate SSL input and set error style.
     * Passphrase requires Key path.
     * Key path must exist.
     */
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

    /**
     * Load stored credentials using master password
     * @param masterPW Master Password
     * @throws AuthenticationException Invalid master password
     */
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
            txtSslKeyPath.setText(((SslCredentials)authInfo).getSslKeyPath());
            txtSslPassphraseHidden.setText(new String(((SslCredentials)authInfo).getSslPassphrase()));
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
