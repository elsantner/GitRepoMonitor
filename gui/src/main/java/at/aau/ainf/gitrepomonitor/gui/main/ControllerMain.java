package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.core.git.PullListener;
import at.aau.ainf.gitrepomonitor.gui.*;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationCellFactory;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.eclipse.jgit.api.MergeResult;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerMain extends StatusBarController implements Initializable, ErrorDisplay, MasterPasswordQuery,
        StatusDisplay, PropertyChangeListener, PullListener {

    @FXML
    private ProgressIndicator indicatorScanRunning;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnCheckStatus;
    @FXML
    private ListView<RepositoryInformation> watchlist;
    @FXML
    private CommitLogView commitLogView;
    @FXML
    private Label lblCommitLog;

    private FileManager fileManager;
    private GitManager gitManager;
    private SecureStorage secureStorage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        super.initialize(url, resourceBundle);
        fileManager = FileManager.getInstance();
        try {
            fileManager.init();
        } catch (IOException e) {
           Logger.getLogger(getClass().getName()).log(Level.SEVERE, "error occurred during file manager init", e);
           showError(ResourceStore.getString("errormsg.file_access_denied"));
        }
        fileManager.addWatchlistListener(this);
        fileManager.addRepoStatusListener(this);
        gitManager = GitManager.getInstance();
        gitManager.setPullListener(this);
        // check repo status
        gitManager.updateWatchlistStatusAsync((success, reposChecked, reposFailed, ex) -> {
            if (!success) {
                displayStatus(ResourceStore.getString("status.updated_n_of_m_repo_status_require_mp",
                        reposChecked, reposChecked+reposFailed));
            } else {
                displayStatus(ResourceStore.getString("status.updated_n_repo_status",
                        reposChecked));
            }
        });
        secureStorage = SecureStorage.getInstance();
        setupUI();
        // TODO: let user choose to cache mp or not
        SecureStorage.getInstance().setCacheMasterPassword(true);
    }

    private void setupUI() {
        watchlist.setCellFactory(new RepositoryInformationCellFactory(this, progessMonitor));
        watchlist.setPlaceholder(new Label(ResourceStore.getString("list.no_entries")));
        watchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setWatchlistDisplay(fileManager.getWatchlist());
        indicatorScanRunning.visibleProperty().bind(ControllerScan.scanRunningProperty());
        indicatorScanRunning.managedProperty().bind(indicatorScanRunning.visibleProperty());
        setupCommitLogDisplay();
    }

    private void setupCommitLogDisplay() {
        watchlist.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                updateCommitLog(newValue));
    }

    private void updateCommitLog(RepositoryInformation repo) {
        if (repo != null) {
            gitManager.getLogAsync(repo.getPath(), (success, changes, ex) -> Platform.runLater(() -> {
                if (success) {
                    lblCommitLog.setText(ResourceStore.getString("commitlog.status", changes.size()));
                    commitLogView.setCommitLog(changes);
                } else {
                    lblCommitLog.setText(ResourceStore.getString("commitlog.no_commits"));
                    commitLogView.setCommitLog(null);
                    displayStatus(ex.getMessage());
                }
            }));
        }
    }

    @FXML
    public void btnScanClicked(ActionEvent actionEvent) {
        try {
            openScanWindow();
        }
        catch (Exception ex) {
            Logger.getAnonymousLogger().severe(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void openScanWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/reposcan/scan.fxml"),
                ResourceStore.getResourceBundle());
        Parent root = loader.load();
        ControllerScan controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("scanpc"));
        stage.setScene(new Scene(root));
        stage.setOnHidden(event -> controller.cleanup());
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }

    @FXML
    public void btnCheckStatusClicked(ActionEvent actionEvent) {
        String masterPW = null;
        if (fileManager.isWatchlistAuthenticationRequired() && !secureStorage.isMasterPasswordCached()) {
            masterPW = showMasterPasswordInputDialog(false);
        }
        displayStatus(ResourceStore.getString("status.update_watchlist_status"));
        btnCheckStatus.setDisable(true);
        gitManager.updateWatchlistStatusAsync(masterPW, (success, reposChecked, reposFailed, ex) -> {
            if (success) {
                displayStatus(ResourceStore.getString("status.updated_n_repo_status", reposChecked));
            } else {
                displayStatus(ResourceStore.getString("status.updated_n_of_m_repo_status_wrong_mp",
                        reposChecked, reposChecked + reposFailed));
            }
            btnCheckStatus.setDisable(false);
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        Platform.runLater(() -> {
            if (e.getPropertyName().equals("watchlist")) {
                setWatchlistDisplay((Collection<RepositoryInformation>)e.getNewValue());
            } else if (e.getPropertyName().equals("repoStatus")) {
                watchlist.refresh();
            }
        });
    }

    private synchronized void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        watchlist.getItems().clear();
        watchlist.getItems().addAll(repoInfo);
        Collections.sort(watchlist.getItems());
    }

    @FXML
    public void btnPullAllClicked(ActionEvent actionEvent) {
        String masterPW = null;
        if (fileManager.isWatchlistAuthenticationRequired() && !secureStorage.isMasterPasswordCached()) {
            masterPW = showMasterPasswordInputDialog(false);
        }
        gitManager.pullWatchlistAsync(masterPW, (results, pullsFailed, wrongMasterPW) -> {
            if (results.isEmpty()) {
                displayStatus("No changes to pull");
            } else {
                if (wrongMasterPW) {
                    displayStatus(ResourceStore.getString("status.pulled_n_of_m_repo_status_wrong_mp",
                            results.size(), (results.size() + pullsFailed)));
                } else {
                    displayStatus(ResourceStore.getString("status.pulled_n_of_m_repo_status",
                            results.size(), (results.size() + pullsFailed)));
                }
                updateCommitLog(watchlist.getSelectionModel().getSelectedItem());
            }
        }, progessMonitor);
    }

    @Override
    public void pullExecuted(String path, MergeResult.MergeStatus status) {
        RepositoryInformation repo = watchlist.getSelectionModel().getSelectedItem();
        if (repo != null && repo.getPath().equals(path)) {
            updateCommitLog(repo);
        }
    }
}
