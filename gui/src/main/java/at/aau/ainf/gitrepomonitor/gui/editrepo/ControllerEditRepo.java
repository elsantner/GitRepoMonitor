package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.GitRepoHelper;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
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
import java.net.URL;
import java.util.ResourceBundle;

public class ControllerEditRepo implements Initializable, ErrorDisplay {

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
    private TextField txtName;
    @FXML
    private TextField txtPath;
    @FXML
    public Button btnTestConnection;
    @FXML
    private Label lblTestConnectionStatus;

    private FileManager fileManager;
    private GitManager gitManager;
    private String originalPath;
    private RepositoryInformation repo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileManager = FileManager.getInstance();
        this.gitManager = GitManager.getInstance();
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

    private void validateTextFieldPath() {
        if (GitRepoHelper.validateRepositoryPath(txtPath.getText())) {
            txtPath.getStyleClass().remove("error-input");
        } else {
            txtPath.getStyleClass().add("error-input");
        }
    }

    public void setRepo(RepositoryInformation repo) {
        this.repo = repo;
        this.originalPath = repo.getPath();
        this.txtName.setText(repo.getName());
        this.txtPath.setText(repo.getPath());
        validateTextFieldPath();
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
            if (!GitRepoHelper.validateRepositoryPath(txtPath.getText())) {
                throw new IllegalArgumentException(ResourceStore.getString("errormsg.invalid_repo_path"));
            }
            RepositoryInformation editedRepo = (RepositoryInformation) repo.clone();
            editedRepo.setPath(txtPath.getText());
            editedRepo.setName(txtName.getText());
            fileManager.editRepo(originalPath, editedRepo);

            Stage stage = (Stage) txtName.getScene().getWindow();
            stage.close();
        } catch (Exception ex) {
            showError(ex.getMessage());
            ex.printStackTrace();
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
            gitManager.testRepoConnectionAsync(repo, txtHttpsUsername.getText(), txtHttpsPasswordHidden.getText(),
                    status -> Platform.runLater(() -> {
                        setConnectionStatusDisplay(status);
                        btnTestConnection.setDisable(false);
                    }));
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
}
