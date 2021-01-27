package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.*;
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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.*;
import javafx.util.Callback;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.UUID;

public class ControllerEditRepo implements Initializable, ErrorDisplay, MasterPasswordQuery {


    @FXML
    public ComboBox<RepositoryInformation.MergeStrategy> cbBoxMergeStrat;
    @FXML
    public CheckBox chkBoxMergeStratApplyAll;
    @FXML
    public TextField txtRemotePath;
    @FXML
    public ComboBox<AuthenticationInformation> cbBoxAuthInfo;
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

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerEditRepo.class.getResource("/at/aau/ainf/gitrepomonitor/gui/editrepo/edit_repo.fxml"),
                ResourceStore.getResourceBundle());
    }

    public static void openWindow(RepositoryInformation repo) throws IOException {
        FXMLLoader loader = ControllerEditRepo.getLoader();
        Parent root = loader.load();
        ((ControllerEditRepo)loader.getController()).setRepo(repo);     // set repo information to display

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("edit_repo"));
        stage.getIcons().add(ResourceStore.getImage("icon_app.png"));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
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
            GitManager.setAuthMethod(repo);
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
        this.txtRemotePath.setText(gitManager.getRemoteURL(repo.getPath()));
        this.cbBoxMergeStrat.getSelectionModel().select(repo.getMergeStrategy().ordinal());

        setupCredentials();
        // select current auth info
        HttpsCredentials tmp = new HttpsCredentials();
        tmp.setID(repo.getAuthID());
        this.cbBoxAuthInfo.getSelectionModel().select(tmp);

        validateTextFields();

        // if repo path is not valid, disable connection test
        btnTestConnection.setVisible(repo.getAuthMethod() != RepositoryInformation.AuthMethod.NONE);
        lblTestConnectionStatus.setVisible(repo.getAuthMethod() != RepositoryInformation.AuthMethod.NONE);
    }

    private void setupCredentials() {
        // set combobox items to all auth infos for repos auth method
        cbBoxAuthInfo.setItems(new ImmutableObservableList<>(
                fileManager.getAllAuthenticationInfos(repo.getAuthMethod()).toArray(new AuthenticationInformation[0])));
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

            // update repo data
            updateRepoInformation();

            Stage stage = (Stage) txtName.getScene().getWindow();
            stage.close();
        } catch (SecurityException ex) {
            showError(ResourceStore.getString("status.wrong_master_password"));
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

    private void updateRepoInformation() {
        RepositoryInformation editedRepo = (RepositoryInformation) repo.clone();
        editedRepo.setPath(txtPath.getText());
        editedRepo.setName(txtName.getText());
        editedRepo.setAuthID(cbBoxAuthInfo.getSelectionModel().getSelectedItem().getID());

        fileManager.editRepo(originalPath, editedRepo);

        if (chkBoxMergeStratApplyAll.isSelected()) {
            fileManager.applyMergeStratToAllRepos(cbBoxMergeStrat.getValue());
        }
    }

    @FXML
    public void onBtnChoosePathClick(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(ResourceStore.getString("edit_repo.selectdir.title"));
        directoryChooser.setInitialDirectory(Utils.getDeepestExistingDirectory(txtPath.getText()));
        File selectedDirectory = directoryChooser.showDialog(txtName.getScene().getWindow());
        if (selectedDirectory != null) {
            txtPath.setText(selectedDirectory.getAbsolutePath());
            handlePathChanged();
        }
    }

    @FXML
    public void onBtnTestConnClick(ActionEvent actionEvent) {
        try {
            btnTestConnection.setDisable(true);
            UUID authID = cbBoxAuthInfo.getSelectionModel().getSelectedItem().getID();

            gitManager.testRepoConnectionAsync(repo, AuthInfo.get(authID,
                    Utils.toCharOrNull(getMasterPasswordIfRequired())),
                    status -> {
                        setConnectionStatusDisplay(status);
                        btnTestConnection.setDisable(false);
            });
        } catch (AuthenticationException | IOException ex) {
            // do nothing, method is aborted automatically
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

    private String getMasterPasswordIfRequired() throws AuthenticationException, IOException {
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
        return masterPW;
    }

    public void onBtnAddAuthInfoClick(MouseEvent mouseEvent) {
        // TODO: Add functionality
    }
}
