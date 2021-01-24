package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.Branch;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.core.git.PullCallback;
import at.aau.ainf.gitrepomonitor.core.git.PullListener;
import at.aau.ainf.gitrepomonitor.gui.*;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationCellFactory;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import at.aau.ainf.gitrepomonitor.gui.settings.ControllerSettings;
import com.sun.javafx.collections.ImmutableObservableList;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerMain extends StatusBarController implements Initializable, ErrorDisplay, MasterPasswordQuery,
        StatusDisplay, PropertyChangeListener, PullListener {

    @FXML
    public Button btnPullAll;
    @FXML
    public ComboBox<Branch> cbBoxBranch;
    @FXML
    private ProgressIndicator indicatorScanRunning;
    @FXML
    private Button btnCheckStatus;
    @FXML
    private ListView<RepositoryInformation> watchlist;
    @FXML
    private CommitLogView commitLogView;
    @FXML
    private Label lblCommitLog;

    /**
     * Stage in which the gui is rendered. Used to display child stages.
     */
    private Stage stage;

    private FileManager fileManager;
    private GitManager gitManager;
    private SecureStorage secureStorage;
    private List<PullCallback.PullResult> pullResults;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerScan.class.getResource("/at/aau/ainf/gitrepomonitor/gui/main/main.fxml"),
                ResourceStore.getResourceBundle());
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        super.initialize(url, resourceBundle);
        fileManager = FileManager.getInstance();
        try {
            if (fileManager.init()) {
                showWarning(ResourceStore.getString("warning.credentials_reset"));
            }
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
        secureStorage = SecureStorage.getImplementation();
        setupUI();
    }

    private void setupUI() {
        watchlist.setCellFactory(new RepositoryInformationCellFactory(this, progessMonitor, this.watchlist, true));
        watchlist.setPlaceholder(new Label(ResourceStore.getString("list.no_entries")));
        watchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setWatchlistDisplay(fileManager.getWatchlist());
        setWatchlistOrder();
        indicatorScanRunning.visibleProperty().bind(ControllerScan.scanRunningProperty());
        indicatorScanRunning.managedProperty().bind(indicatorScanRunning.visibleProperty());
        setupCommitLogDisplay();
        setupSwitchBranch();
    }

    private void setupSwitchBranch() {
        cbBoxBranch.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                RepositoryInformation repo = watchlist.getSelectionModel().getSelectedItem();
                if (oldValue != null && repo != null && newValue != null) {
                    // if remote branch was selected, create it locally before checkout
                    if (newValue.isRemoteOnly()) {
                        gitManager.createBranch(repo.getPath(), newValue.getShortName());
                        gitManager.checkout(repo.getPath(), newValue.getShortName());
                        updateBranches(repo);
                    } else {
                        gitManager.checkout(repo.getPath(), newValue.getShortName());
                    }
                    updateCommitLog(repo);
                }
            } catch (CheckoutConflictException ex) {
                cbBoxBranch.getSelectionModel().select(oldValue);
                showError(ex.getLocalizedMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        cbBoxBranch.setCellFactory(param -> new BranchListCell());
    }

    private void setupCommitLogDisplay() {
        watchlist.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            updateBranches(newValue);
            updateCommitLog(newValue);
            // clear "New"-icon when deselecting
            if (oldValue != null && newValue != null) {
                fileManager.setNewChanges(oldValue.getPath(), 0);
            }
        });
    }

    /**
     * Update branch selection ComboBox according to given repo
     * @param repo Repo
     */
    private void updateBranches(RepositoryInformation repo) {
        try {
            Collection<Branch> branchNames;
            try {
                branchNames = gitManager.getBranchNames(repo.getPath());
            } catch (Exception e) {
                branchNames = new ArrayList<>();
            }
            if (!branchNames.isEmpty()) {
                Branch[] branches = branchNames.toArray(new Branch[0]);
                Arrays.sort(branches);
                cbBoxBranch.setItems(new ImmutableObservableList<>(branches));
                cbBoxBranch.getSelectionModel().select(gitManager.getSelectedBranch(repo.getPath()));
                cbBoxBranch.setDisable(false);
            } else {
                cbBoxBranch.setItems(null);
                cbBoxBranch.setDisable(true);
            }
        } catch (Exception e) {
            cbBoxBranch.setItems(null);
            cbBoxBranch.setDisable(true);
        }
    }

    private void updateCommitLog(RepositoryInformation repo) {
        if (repo != null) {
            gitManager.getLogAsync(repo.getPath(), (success, changes, ex) -> Platform.runLater(() -> {
                if (success) {
                    lblCommitLog.setText(ResourceStore.getString("commitlog.status", changes.size()));
                    commitLogView.setCommitLog(changes, repo.getNewCommitCount());
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
        FXMLLoader loader = ControllerScan.getLoader();
        Parent root = loader.load();
        ControllerScan controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("scanpc"));
        stage.getIcons().add(ResourceStore.getImage("icon_app.png"));
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
        gitManager.updateWatchlistStatusAsync(Utils.toCharOrNull(masterPW), (success, reposChecked, reposFailed, ex) -> {
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

    /**
     * Set the custom index property according to the current position in the rendered watchlist
     * NOTE: This does NOT persist any data and is in it's own a temporary measure.
     */
    private void setWatchlistOrder() {
        for (int i=0; i<watchlist.getItems().size(); i++) {
            watchlist.getItems().get(i).setCustomOrderIndex(i);
        }
    }

    @FXML
    public void btnPullAllClicked(ActionEvent actionEvent) {
        String masterPW = null;
        if (fileManager.isWatchlistAuthenticationRequired() && !secureStorage.isMasterPasswordCached()) {
            masterPW = showMasterPasswordInputDialog(false);
        }
        btnPullAll.setDisable(true);
        gitManager.pullWatchlistAsync(Utils.toCharOrNull(masterPW), (results, pullsSuccess, pullsFailed, wrongMasterPW) -> {
            if (results.isEmpty()) {
                displayStatus(ResourceStore.getString("status.pull_no_changes"));
            } else {
                if (wrongMasterPW) {
                    displayStatus(ResourceStore.getString("status.pulled_n_of_m_repo_status_wrong_mp",
                            pullsSuccess, (pullsSuccess + pullsFailed)));
                } else {
                    displayStatus(ResourceStore.getString("status.pulled_n_of_m_repo_status",
                            pullsSuccess, (pullsSuccess + pullsFailed)));
                }
            }
            // store results for detailed display
            this.pullResults = results;
            btnPullAll.setDisable(false);
        }, progessMonitor);
    }

    @Override
    public void pullExecuted(String path, MergeResult.MergeStatus status) {
        RepositoryInformation repo = watchlist.getSelectionModel().getSelectedItem();
        if (repo != null && repo.getPath().equals(path)) {
            updateCommitLog(repo);
        }
    }

    @FXML
    public void btnSettingsClicked(ActionEvent actionEvent) throws IOException {
        FXMLLoader loader = ControllerSettings.getLoader();
        Parent root = loader.load();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("settings"));
        stage.getIcons().add(ResourceStore.getImage("icon_app.png"));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }

    @Override
    public void displayStatus(String status) {
        super.displayStatus(status);
        this.pullResults = null;
    }

    /**
     * Open pull details window when clicking on status bar after pull all
     */
    @FXML
    public void onLabelStatusClicked(MouseEvent mouseEvent) {
        try {
            if (this.pullResults != null) {
                FXMLLoader loader = ControllerPullResults.getLoader();
                Parent root = loader.load();
                ((ControllerPullResults) loader.getController()).setDisplay(pullResults);
                // get absolute coordinates of label on screen
                Bounds boundsInScreen = lblStatus.localToScreen(lblStatus.getBoundsInLocal());

                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.initStyle(StageStyle.UNDECORATED);
                // show in the same window as the rest of the GUI
                stage.initOwner(this.stage);
                stage.setScene(new Scene(root));
                stage.sizeToScene();
                stage.show();
                stage.setWidth(lblStatus.getWidth());
                stage.setMinWidth(stage.getWidth());
                stage.setMinHeight(stage.getHeight());
                // display on top of label
                stage.setX(boundsInScreen.getMinX());
                stage.setY(boundsInScreen.getMinY()-stage.getHeight());

                // close window if clicked outside
                stage.focusedProperty().addListener((observable, oldValue, newValue) -> {
                    if (!newValue) {
                        stage.hide();
                    }
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
