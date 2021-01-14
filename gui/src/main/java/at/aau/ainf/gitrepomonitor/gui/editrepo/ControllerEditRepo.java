package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SSLInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import com.sun.javafx.collections.ImmutableObservableList;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
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
    public AnchorPane authContainerSSL;
    @FXML
    public ImageView iconShowPW;
    @FXML
    public Tooltip ttShowPW;
    @FXML
    public Button btnLoadCredentials;
    @FXML
    public ComboBox<RepositoryInformation.MergeStrategy> cbBoxMergeStrat;
    @FXML
    public CheckBox chkBoxMergeStratApplyAll;
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
    public AnchorPane authContainerNone;
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

    private boolean authInfoChanged;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerEditRepo.class.getResource("/at/aau/ainf/gitrepomonitor/gui/editrepo/edit_repo.fxml"),
                ResourceStore.getResourceBundle());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileManager = FileManager.getInstance();
        this.gitManager = GitManager.getInstance();
        this.secureStorage = SecureStorage.getImplementation();
        setupUI();
    }

    private void setupUI() {
        txtPath.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(!newValue) { // when losing focus, check validity of input
                validateTextFieldPath();
            }
        });
        // only show selected credential input
        authContainerNone.managedProperty().bind(authContainerNone.visibleProperty());
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

        cbBoxMergeStrat.setItems(new ImmutableObservableList<>(RepositoryInformation.MergeStrategy.values()));
        cbBoxMergeStrat.setCellFactory(new Callback<>() {
            @Override
            public ListCell<RepositoryInformation.MergeStrategy> call(ListView<RepositoryInformation.MergeStrategy> item) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(RepositoryInformation.MergeStrategy item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null || empty) {
                            setGraphic(null);
                            setTooltip(null);
                        } else {
                            setText(item.toString());
                            setTooltip(new Tooltip(getTooltipText(item)));
                        }
                    }
                };
            }
        });

        txtPath.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                handlePathChanged();
            }
        });
    }

    /**
     * Validate the repo path and update the displayed authentication information.
     */
    private void handlePathChanged() {
        validateTextFieldPath();
        repo.setPath(txtPath.getText());
        try {
            FileManager.setAuthMethod(repo);
            updateRepoDisplay(repo);
        } catch (IOException e) {
            // should not happen
        }
    }

    private String getTooltipText(RepositoryInformation.MergeStrategy item) {
        switch (item) {
            case OURS:
                return ResourceStore.getString("merge_strat.ours.tooltip");
            case THEIRS:
                return ResourceStore.getString("merge_strat.theirs.tooltip");
            //case RESOLVE:
            //    return ResourceStore.getString("merge_strat.resolve.tooltip");
            case RECURSIVE:
                return ResourceStore.getString("merge_strat.recursive.tooltip");
            //case SIMPLE_TWO_WAY_IN_CORE:
            //    return ResourceStore.getString("merge_strat.2way.tooltip");
            default:
                return null;
        }
    }

    private boolean validateTextFields() {
        return validateTextFieldPath();
    }

    private boolean validateTextFieldPath() {
        boolean status;
        if (status = Utils.validateRepositoryPath(txtPath.getText())) {
            // remove all occurrences of the style class (might be added multiple times due to timing of focus loss)
            txtPath.getStyleClass().removeAll("error-input");
        } else {
            txtPath.getStyleClass().add("error-input");
        }
        return status;
    }

    /**
     * Sets the repository to be edited. Call this method only once per controller!
     * @param repo Repository to be edited
     */
    public void setRepo(RepositoryInformation repo) {
        this.repo = repo;
        // remember original path before change
        this.originalPath = repo.getPath();
        updateRepoDisplay(repo);
    }

    /**
     * Update the displayed information.
     * @param repo Repository information to be displayed.
     */
    private void updateRepoDisplay(RepositoryInformation repo) {
        this.txtName.setText(repo.getName());
        this.txtPath.setText(repo.getPath());
        this.cbBoxMergeStrat.getSelectionModel().select(repo.getMergeStrategy().ordinal());
        this.txtSslKeyPath.setText(repo.getSslKeyPath());

        setupCredentials();
        setupCredentialChangeListener();
        validateTextFields();

        // if repo path is not valid, disable connection test
        btnTestConnection.setVisible(repo.getAuthMethod() != RepositoryInformation.AuthMethod.NONE);
        lblTestConnectionStatus.setVisible(repo.getAuthMethod() != RepositoryInformation.AuthMethod.NONE);
    }

    private void setupCredentials() {
        setAuthenticationMethod();
        // if auth method is not NONE, then there must be stored credentials
        boolean showCredentials = repo.isAuthenticated() && repo.getAuthMethod() != RepositoryInformation.AuthMethod.NONE;
        btnLoadCredentials.setVisible(showCredentials);

        if (showCredentials) {
            // load credentials if mp is cached
            if (secureStorage.isMasterPasswordCached()) {
                try {
                    loadCredentials(null);
                } catch (SecurityException e) {
                    showError(ResourceStore.getString("status.wrong_master_password"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError(ex.getMessage());
                }
            } else {
                txtHttpsUsername.setPromptText(ResourceStore.getString("edit_repo.stored_username"));
                txtHttpsPasswordHidden.setPromptText(ResourceStore.getString("edit_repo.stored_password"));
                txtHttpsPasswordShown.setPromptText(ResourceStore.getString("edit_repo.stored_password"));
                txtSslPassphraseHidden.setPromptText(ResourceStore.getString("edit_repo.stored_passphrase"));
                txtSslPassphraseShown.setPromptText(ResourceStore.getString("edit_repo.stored_passphrase"));
            }
        }
    }

    private void setAuthenticationMethod() {
        switch (repo.getAuthMethod()) {
            case HTTPS:
                authContainerHTTPS.setVisible(true);
                authContainerSSL.setVisible(false);
                authContainerNone.setVisible(false);
                break;
            case SSL:
                authContainerHTTPS.setVisible(false);
                authContainerSSL.setVisible(true);
                authContainerNone.setVisible(false);
                break;
            case NONE:
                authContainerHTTPS.setVisible(false);
                authContainerSSL.setVisible(false);
                authContainerNone.setVisible(true);
                break;
        }
    }

    private void setupCredentialChangeListener() {
        txtHttpsUsername.textProperty().addListener((observable, oldValue, newValue) -> {
            authInfoChanged = true;
            clearHttpsInputPrompts();
        });
        txtHttpsPasswordHidden.textProperty().addListener((observable, oldValue, newValue) -> {
            authInfoChanged = true;
            clearHttpsInputPrompts();
        });
        txtSslKeyPath.textProperty().addListener((observable, oldValue, newValue) -> authInfoChanged = true);
        txtSslPassphraseHidden.textProperty().addListener((observable, oldValue, newValue) -> authInfoChanged = true);
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

            if (repo.getAuthMethod() == RepositoryInformation.AuthMethod.NONE) {
                if (!showNoRemoteWarningDialog()) {
                    return;     // abort method if user does not confirm their intend
                }
            }

            if (chkBoxMergeStratApplyAll.isSelected()) {
                if (!showConfirmMergeStratApplyAllDialog()) {
                    return;     // abort method if user does not confirm their intend
                }
            }

            // only require master pw if credentials were actually changed
            if (wasAuthChanged()) {
                handleAuthChanged();
            }
            // update repo data
            updateRepoInformation();

            Stage stage = (Stage) txtName.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showError(ResourceStore.getString("status.wrong_master_password"));
        } catch (AuthenticationException ex) {
            // abort method
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private boolean showConfirmMergeStratApplyAllDialog() {
        return showConfirmationDialog(Alert.AlertType.CONFIRMATION,
                ResourceStore.getString("edit_repo.merge_strat_apply_all.title"),
                ResourceStore.getString("edit_repo.merge_strat_apply_all.header"),
                ResourceStore.getString("edit_repo.merge_strat_apply_all.content"));
    }

    private boolean showNoRemoteWarningDialog() {
        return showConfirmationDialog(Alert.AlertType.WARNING,
                ResourceStore.getString("edit_repo.warn_no_remote.title"),
                ResourceStore.getString("edit_repo.warn_no_remote.header"),
                ResourceStore.getString("edit_repo.warn_no_remote.content"));
    }

    private void handleAuthChanged() throws IOException, AuthenticationException {
        // master password is required whenever auth information is changed
        String masterPW = null;
        if (secureStorage.isMasterPasswordSet()) {
            if (!secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
                // if master password dialog was aborted, abort method
                if (masterPW == null) {
                    throw new AuthenticationException(ResourceStore.getString("errormsg.mp_dialog_aborted"));
                }
            }
        } else {
            masterPW = showMasterPasswordInputDialog(true);
            if (masterPW != null) {
                secureStorage.setMasterPassword(Utils.toCharOrNull(masterPW));
            } else {
                // if master password dialog was aborted, abort method
                throw new AuthenticationException(ResourceStore.getString("errormsg.mp_dialog_aborted"));
            }
        }

        if (authContainerHTTPS.isVisible()) {
            if (defaultAuthValuesSet()) {
                secureStorage.deleteHttpsCredentials(Utils.toCharOrNull(masterPW), repo.getID());
            } else {
                secureStorage.storeHttpsCredentials(Utils.toCharOrNull(masterPW), repo.getID(),
                        txtHttpsUsername.getText(), txtHttpsPasswordHidden.getText().toCharArray());
            }
        } else if (authContainerSSL.isVisible()) {
            validateSslInformation();
            if (defaultAuthValuesSet()) {
                secureStorage.deleteSslInformation(Utils.toCharOrNull(masterPW), repo.getID());
            } else {
                secureStorage.storeSslInformation(Utils.toCharOrNull(masterPW), repo.getID(),
                        txtSslPassphraseHidden.getText().getBytes());
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

    /**
     * Check if the authentication method was changed in the GUI
     * @return True, if changed
     */
    private boolean wasAuthChanged() {
        return authInfoChanged;
    }

    private void updateRepoInformation() {
        RepositoryInformation editedRepo = (RepositoryInformation) repo.clone();
        editedRepo.setPath(txtPath.getText());
        editedRepo.setName(txtName.getText());
        // only set authentication if auth was changed
        if (wasAuthChanged()) {
            editedRepo.setAuthenticated(!defaultAuthValuesSet());
        }
        editedRepo.setMergeStrategy(cbBoxMergeStrat.getValue());
        if (authContainerSSL.isVisible()) {
            editedRepo.setSslKeyPath(txtSslKeyPath.getText());
        }

        fileManager.editRepo(originalPath, editedRepo);

        if (chkBoxMergeStratApplyAll.isSelected()) {
            fileManager.applyMergeStratToAllRepos(cbBoxMergeStrat.getValue());
        }
    }

    private boolean defaultAuthValuesSet() {
        if (authContainerHTTPS.isVisible()) {
            return txtHttpsUsername.getText().isBlank() && txtHttpsPasswordHidden.getText().isBlank();
        } else if (authContainerSSL.isVisible())  {
            return txtSslPassphraseHidden.getText().isBlank();
        } else {
            // no remote --> no auth info
            return true;
        }
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

    @FXML
    public void onBtnChoosePathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(ResourceStore.getString("edit_repo.selectdir.title"));
        directoryChooser.setInitialDirectory(getDeepestExistingDirectory(txtPath.getText()));
        File selectedDirectory = directoryChooser.showDialog(txtName.getScene().getWindow());
        if (selectedDirectory != null) {
            txtPath.setText(selectedDirectory.getAbsolutePath());
            handlePathChanged();
        }
    }

    @FXML
    public void onBtnTestConnClick(ActionEvent actionEvent) {
        btnTestConnection.setDisable(true);
        // if credentials are stored but not loaded, prompt the user to input Master Password and load them
        // (if user aborts MP input, abort connection test)
        // else use text from TextFields
        boolean credentialsSet = true;
        if (btnLoadCredentials.isVisible() && !authInfoChanged) {
            credentialsSet = onBtnLoadCredentialsClick(actionEvent);
        }

        if (authContainerHTTPS.isVisible()) {
            if (credentialsSet) {
                gitManager.testRepoConnectionHttpsAsync(repo, txtHttpsUsername.getText(), txtHttpsPasswordHidden.getText(),
                        status -> Platform.runLater(() -> {
                            setConnectionStatusDisplay(status);
                            btnTestConnection.setDisable(false);
                        }));
            } else {
                btnTestConnection.setDisable(false);
            }
        } else if (authContainerSSL.isVisible()) {
            if (credentialsSet) {
                String path = txtSslKeyPath.getText().isBlank() ? null : txtSslKeyPath.getText();
                String passphrase = txtSslPassphraseHidden.getText().isBlank() ? null : txtSslPassphraseHidden.getText();

                gitManager.testRepoConnectionSslAsync(repo, path, Utils.toBytesOrNull(passphrase),
                        status -> Platform.runLater(() -> {
                            setConnectionStatusDisplay(status);
                            btnTestConnection.setDisable(false);
                        }));
            } else {
                btnTestConnection.setDisable(false);
            }
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
            showError(ResourceStore.getString("status.wrong_master_password"));
            return false;
        } catch (Exception e) {
            showError(e.getMessage());
            return false;
        }
    }

    private void loadCredentials(String masterPW) throws IOException {
        if (authContainerHTTPS.isVisible()) {
            HttpsCredentials credentials = secureStorage.getHttpsCredentials(Utils.toCharOrNull(masterPW), repo.getID());
            txtHttpsUsername.setText(credentials.getUsername());
            txtHttpsPasswordHidden.setText(new String(credentials.getPassword()));
        } else if (authContainerSSL.isVisible()) {
            SSLInformation sslInfo = secureStorage.getSslInformation(Utils.toCharOrNull(masterPW), repo.getID());
            txtSslPassphraseHidden.setText(new String(sslInfo.getSslPassphrase()));
        }
        btnLoadCredentials.setVisible(false);
        // all previous changes are overwritten --> no changes made yet
        authInfoChanged = false;
    }

    public void onBtnChooseSslKeyPathClick(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(ResourceStore.getString("edit_repo.selectdir.title"));
        fileChooser.setInitialDirectory(getDeepestExistingDirectory(txtSslKeyPath.getText()));
        File selectedFile = fileChooser.showOpenDialog(txtName.getScene().getWindow());
        if (selectedFile != null) {
            txtSslKeyPath.setText(selectedFile.getAbsolutePath());
            //validateTextFieldPath();
        }
    }
}
