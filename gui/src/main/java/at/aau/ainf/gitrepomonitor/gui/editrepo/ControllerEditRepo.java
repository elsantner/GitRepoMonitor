package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ControllerEditRepo implements Initializable, ErrorDisplay, MasterPasswordQuery {

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
    public RadioButton radioBtnNone;
    @FXML
    public RadioButton radioBtnHttps;
    @FXML
    public RadioButton radioBtnSSL;
    @FXML
    public AnchorPane authContainerSSL;
    @FXML
    public ImageView iconShowPW;
    @FXML
    public Tooltip ttShowPW;
    @FXML
    public Button btnLoadCredentials;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtPath;
    @FXML
    public Button btnTestConnection;
    @FXML
    private Label lblTestConnectionStatus;

    private FileManager fileManager;
    private SecureStorage secureStorage;
    private GitManager gitManager;
    private String originalPath;
    private RepositoryInformation repo;

    private boolean httpsCredentialsChanged;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileManager = FileManager.getInstance();
        this.gitManager = GitManager.getInstance();
        this.secureStorage = SecureStorage.getInstance();
        setupUI();
    }

    private void setupUI() {
        txtPath.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(!newValue) { // when losing focus, check validity of input
                validateTextFieldPath();
            }
        });
        // only show selected credential input
        authContainerHTTPS.managedProperty().bind(authContainerHTTPS.visibleProperty());
        authContainerHTTPS.visibleProperty().bind(radioBtnHttps.selectedProperty());
        authContainerSSL.managedProperty().bind(authContainerSSL.visibleProperty());
        authContainerSSL.visibleProperty().bind(radioBtnSSL.selectedProperty());

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
    }

    private boolean validateTextFields() {
        return validateTextFieldPath();
    }

    private boolean validateHttpsCredentials() {
        boolean status = false;
        if (radioBtnHttps.isSelected()) {
            status = txtHttpsUsername.getText().isBlank();
            if (status) {
                txtHttpsUsername.getStyleClass().add("error-input");
            } else {
                txtHttpsUsername.getStyleClass().remove("error-input");
            }
        }
        return !status;
    }

    private boolean validateTextFieldPath() {
        boolean status;
        if (status = Utils.validateRepositoryPath(txtPath.getText())) {
            txtPath.getStyleClass().remove("error-input");
        } else {
            txtPath.getStyleClass().add("error-input");
        }
        return status;
    }

    public void setRepo(RepositoryInformation repo) {
        this.repo = repo;
        this.originalPath = repo.getPath();
        this.txtName.setText(repo.getName());
        this.txtPath.setText(repo.getPath());

        setupCredentials();
        setupCredentialChangeListener();
        validateTextFields();
    }

    private void setupCredentials() {
        setAuthenticationMethod();
        // if auth method is not NONE, then there must be stored credentials
        if (!repo.isAuthenticated()) {
            btnLoadCredentials.setVisible(false);
        } else if (repo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
            // load credentials if mp is cached
            if (secureStorage.isMasterPasswordCached()) {
                try {
                    loadCredentials(null);
                } catch (SecurityException e) {
                    showError("Wrong Master Password");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError(ex.getMessage());
                }
            } else {
                txtHttpsUsername.setPromptText("Stored Username");
                txtHttpsPasswordHidden.setPromptText("Stored Password");
                txtHttpsPasswordShown.setPromptText("Stored Password");
            }
        }
    }

    private void setAuthenticationMethod() {
        switch (repo.getAuthMethod()) {
            case NONE:
                radioBtnNone.setSelected(true);
                break;
            case HTTPS:
                radioBtnHttps.setSelected(true);
                break;
            case SSH:
                radioBtnSSL.setSelected(true);
                break;
        }
    }

    private void setupCredentialChangeListener() {
        txtHttpsUsername.textProperty().addListener((observable, oldValue, newValue) -> {
            httpsCredentialsChanged = true;
            clearHttpsInputPrompts();
        });
        txtHttpsPasswordHidden.textProperty().addListener((observable, oldValue, newValue) -> {
            httpsCredentialsChanged = true;
            clearHttpsInputPrompts();
        });
    }

    private void clearHttpsInputPrompts() {
        txtHttpsUsername.setPromptText(null);
        txtHttpsPasswordHidden.setPromptText(null);
        txtHttpsPasswordShown.setPromptText(null);
    }

    @FXML
    public void onBtnCancelClick(ActionEvent actionEvent) {
        Stage stage = (Stage) txtName.getScene().getWindow();
        stage.setMinHeight(stage.getHeight());
        stage.close();
    }

    @FXML
    public void onBtnSaveClick(ActionEvent actionEvent) {
        try {
            if (!Utils.validateRepositoryPath(txtPath.getText())) {
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.invalid_repo_path"));
            }

            // only require master pw if credentials wre actually changed
            if (wasAuthChanged()) {
                // TODO: remove credentials if none was selected (or only delete when explicitly requested?)
                if (radioBtnHttps.isSelected()) {
                    if (!validateHttpsCredentials()) {
                        throw new IllegalArgumentException("HTTPS username must not be empty");
                    }

                    String masterPW = null;
                    if (secureStorage.isMasterPasswordSet()) {
                        if (secureStorage.isMasterPasswordCached()) {
                            masterPW = showMasterPasswordInputDialog(false);
                        }
                    } else {
                        masterPW = showMasterPasswordInputDialog(true);
                        if (masterPW != null) {
                            secureStorage.setMasterPassword(Utils.toCharOrNull(masterPW));
                        }
                    }
                    // if master password dialog was aborted, abort method
                    if (masterPW == null) {
                        return;
                    }
                    secureStorage.storeHttpsCredentials(Utils.toCharOrNull(masterPW), repo.getID(),
                            txtHttpsUsername.getText(), txtHttpsPasswordHidden.getText().toCharArray());
                }
            }
            // update repo data
            updateRepoInformation(getSelectedAuthMethod());

            Stage stage = (Stage) txtName.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showError("Wrong Master Password");
        } catch (Exception ex) {
            showError(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private RepositoryInformation.AuthMethod getSelectedAuthMethod() {
        if (radioBtnNone.isSelected())
            return RepositoryInformation.AuthMethod.NONE;
        else if (radioBtnHttps.isSelected())
            return RepositoryInformation.AuthMethod.HTTPS;
        else
            return RepositoryInformation.AuthMethod.SSH;
    }

    /**
     * Check if the authentication method was changed in the GUI
     * @return True, if changed
     */
    private boolean wasAuthChanged() {
        switch (repo.getAuthMethod()) {
            case NONE:
                return !radioBtnNone.isSelected();
            case HTTPS:
                return !radioBtnHttps.isSelected() || httpsCredentialsChanged;
            case SSH:
                return !radioBtnSSL.isSelected();
        }
        return false;
    }

    private void updateRepoInformation(RepositoryInformation.AuthMethod authMethod) {
        RepositoryInformation editedRepo = (RepositoryInformation) repo.clone();
        editedRepo.setPath(txtPath.getText());
        editedRepo.setName(txtName.getText());
        editedRepo.setAuthMethod(authMethod);
        fileManager.editRepo(originalPath, editedRepo);
    }

    private File getDeepestExistingDirectory(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        File dir = new File(path);
        while (!dir.exists() || !dir.isDirectory()) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    public void onBtnChoosePathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(ResourceStore.getString("edit_repo.selectdir.title"));
        directoryChooser.setInitialDirectory(getDeepestExistingDirectory(txtPath.getText()));
        File selectedDirectory = directoryChooser.showDialog(txtName.getScene().getWindow());
        if (selectedDirectory != null) {
            txtPath.setText(selectedDirectory.getAbsolutePath());
            validateTextFieldPath();
        }
    }

    @FXML
    public void onBtnTestConnClick(ActionEvent actionEvent) {
        btnTestConnection.setDisable(true);
        if (radioBtnNone.isSelected()) {
            gitManager.testRepoConnectionAsync(repo, status -> Platform.runLater(() -> {
                setConnectionStatusDisplay(status);
                btnTestConnection.setDisable(false);
            }));
        } else if (radioBtnHttps.isSelected()) {
            // if credentials are stored but not loaded, prompt the user to input Master Password and load them
            // (if user aborts MP input, abort connection test)
            // else use text from TextFields
            boolean credentialsSet = true;
            if (btnLoadCredentials.isVisible() && !httpsCredentialsChanged) {
                credentialsSet = onBtnLoadCredentialsClick(actionEvent);
            }
            if (credentialsSet) {
                gitManager.testRepoConnectionAsync(repo, txtHttpsUsername.getText(), txtHttpsPasswordHidden.getText(),
                        status -> Platform.runLater(() -> {
                            setConnectionStatusDisplay(status);
                            btnTestConnection.setDisable(false);
                        }));
            } else {
                btnTestConnection.setDisable(false);
            }
        } else {
            btnTestConnection.setDisable(false);
        }

    }

    private void setConnectionStatusDisplay(RepositoryInformation.RepoStatus status) {
        String statusStringKey;
        boolean success = false;
        if (status == RepositoryInformation.RepoStatus.PATH_INVALID) {
            statusStringKey = "status.repo.invalid_path";
        } else if (status == RepositoryInformation.RepoStatus.NO_REMOTE) {
            statusStringKey = "status.repo.no_remote";
        } else if (status == RepositoryInformation.RepoStatus.INACCESSIBLE_REMOTE) {
            statusStringKey = "status.repo.no_auth_info";
        } else if (status == RepositoryInformation.RepoStatus.UNKNOWN_ERROR) {
            statusStringKey = "status.repo.unknown_error";
        } else {
            statusStringKey = "status.connection.success";
            success = true;
        }

        lblTestConnectionStatus.setText(ResourceStore.getString(statusStringKey));
        if (success) {
            lblTestConnectionStatus.getStyleClass().remove("failure");
            lblTestConnectionStatus.getStyleClass().add("success");
        } else {
            lblTestConnectionStatus.getStyleClass().remove("success");
            lblTestConnectionStatus.getStyleClass().add("failure");
        }
    }

    @FXML
    public boolean onBtnLoadCredentialsClick(ActionEvent actionEvent) {
        try {
            String masterPW = showMasterPasswordInputDialog(false);
            if (masterPW != null) {
                loadCredentials(masterPW);
                return true;
            } else {
                return false;
            }
        } catch (SecurityException e) {
            showError("Wrong Master Password");
            return false;
        } catch (Exception e) {
            showError(e.getMessage());
            return false;
        }
    }

    private void loadCredentials(String masterPW) throws IOException {
        HttpsCredentials credentials = secureStorage.getHttpsCredentials(Utils.toCharOrNull(masterPW), repo.getID());
        txtHttpsUsername.setText(credentials.getUsername());
        txtHttpsPasswordHidden.setText(new String(credentials.getPassword()));
        btnLoadCredentials.setVisible(false);
        // all previous changes are overwritten --> no changes made yet
        httpsCredentialsChanged = false;
    }
}
