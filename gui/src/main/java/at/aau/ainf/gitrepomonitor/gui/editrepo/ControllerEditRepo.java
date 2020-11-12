package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.GitRepoHelper;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class ControllerEditRepo implements Initializable, ErrorDisplay {

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtPath;

    private FileManager fileManager;
    private String originalPath;
    private RepositoryInformation repo;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.fileManager = FileManager.getInstance();
        setupUI();
    }

    private void setupUI() {
        txtPath.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if(!newValue) { // when losing focus, check validity of input
                validateTextFieldPath();
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
}
