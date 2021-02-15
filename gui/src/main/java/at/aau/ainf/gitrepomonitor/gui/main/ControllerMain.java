package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.files.*;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.*;
import at.aau.ainf.gitrepomonitor.gui.*;
import at.aau.ainf.gitrepomonitor.gui.auth.ControllerAuthList;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationKeyPressHandler;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationNameCell;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationTableRowFactory;
import at.aau.ainf.gitrepomonitor.gui.reposcan.ControllerScan;
import at.aau.ainf.gitrepomonitor.gui.settings.ControllerSettings;
import com.sun.javafx.collections.ImmutableObservableList;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.lib.PersonIdent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for main window.
 */
public class ControllerMain extends StatusBarController implements Initializable, AlertDisplay, MasterPasswordQuery,
        StatusDisplay, PropertyChangeListener, PullListener, FileErrorListener {

    private static final DateFormat df = new SimpleDateFormat(ResourceStore.getString("date_time_format"));

    @FXML
    public Button btnPullAll;
    @FXML
    public ComboBox<Branch> cbBoxBranch;
    @FXML
    public TableView<RepositoryInformation> tblWatchlist;
    @FXML
    private ProgressIndicator indicatorScanRunning;
    @FXML
    private Button btnCheckStatus;
    @FXML
    private CommitLogView commitLogView;
    @FXML
    private Label lblCommitLog;
    @FXML
    public TableColumn<RepositoryInformation, RepositoryInformation> wlColName;
    @FXML
    public TableColumn<RepositoryInformation, Date> wlColLastChange;
    @FXML
    public TableColumn<RepositoryInformation, PersonIdent> wlColPerson;

    /**
     * Stage in which the gui is rendered. Used to display child stages.
     */
    private Stage stage;

    private FileManager fileManager;
    private GitManager gitManager;
    private SecureStorage secureStorage;
    private List<PullCallback.PullResult> pullResults;

    /**
     * Get FXML loader for this GUI component.
     * @return configured FXML loader
     */
    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerScan.class.getResource("/at/aau/ainf/gitrepomonitor/gui/main/main.fxml"),
                ResourceStore.getResourceBundle());
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        super.initialize(url, resourceBundle);

        fileManager = FileManager.getInstance();
        try {
            // check if data path is accessible and take action
            if (!StoragePath.isFirstUse()) {
                checkAndHandleDataInaccessible();
            }
            fileManager.init();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException | IOException e) {
            // if severe error happens close program
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "error occurred during file manager init", e);
            showError(ResourceStore.getString("errormsg.file_access_denied"));
            System.exit(-1);
        }

        fileManager.addWatchlistListener(this);
        fileManager.addRepoStatusListener(this);
        fileManager.setFileErrorListener(this);

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

    /**
     * Show dialog until user takes successful action
     */
    private void checkAndHandleDataInaccessible() throws IOException {
        boolean done = false;
        while (!fileManager.isDatabaseAccessible() && !done) {
            switch (showDatabaseInaccessibleDialog()) {
                case 0:
                    System.exit(0);
                    break;
                case 3:
                    StoragePath.resetToDefaultPath();
                    FileManager.getInstance().storagePathChanged();
                    Settings.storagePathChanged();
                    done = true;
                    break;
            }
        }
    }

    /**
     * Ask the user what to do if data cannot be accessed.
     * @return 0 if dialog was aborted.
     *         1 if retry selected
     *         3 if reset selected
     */
    private int showDatabaseInaccessibleDialog() {
        ButtonType retry = new ButtonType(ResourceStore.getString("error.data_inaccessible.retry"), ButtonBar.ButtonData.OK_DONE);
        ButtonType reset = new ButtonType(ResourceStore.getString("error.data_inaccessible.reset"), ButtonBar.ButtonData.APPLY);
        ButtonType cancel = new ButtonType(ResourceStore.getString("error.data_inaccessible.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.ERROR,
                ResourceStore.getString("error.data_inaccessible.content", StoragePath.getCurrentPath()),
                retry, reset, cancel);

        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().add(ResourceStore.getImage("icon_app.png"));
        alert.setTitle(ResourceStore.getString("error.data_inaccessible.title"));
        alert.setHeaderText(ResourceStore.getString("error.data_inaccessible.header"));
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty()) {
            return 0;
        } else {
            switch (result.get().getButtonData()) {
                case OK_DONE:
                    return 1;
                case APPLY:
                    return 3;
                default:
                    return 0;
            }
        }
    }

    private void setupUI() {
        setupTable();
        setWatchlistDisplay(fileManager.getWatchlist());
        indicatorScanRunning.visibleProperty().bind(ControllerScan.scanRunningProperty());
        indicatorScanRunning.managedProperty().bind(indicatorScanRunning.visibleProperty());
        setupCommitLogDisplay();
        setupSwitchBranch();
        setupDragAndDropRepoAdd();
    }

    private void setupTable() {
        tblWatchlist.setRowFactory(new RepositoryInformationTableRowFactory(this, progessMonitor));
        tblWatchlist.setPlaceholder(new Label(ResourceStore.getString("repo_list.no_entries")));
        tblWatchlist.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tblWatchlist.setOnKeyPressed(new RepositoryInformationKeyPressHandler(tblWatchlist));

        wlColName.setCellValueFactory(new PropertyValueFactory<>("reflect"));
        wlColName.setCellFactory(param -> new RepositoryInformationNameCell());

        wlColLastChange.setCellValueFactory(new PropertyValueFactory<>("lastCommitDate"));
        wlColLastChange.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(Date item, boolean empty) {
                if (empty) {
                    setText(null);
                } else {
                    if (item != null) {
                        setText(df.format(item));
                    } else {
                        setText(ResourceStore.getString("watchlist.last_change.placeholder"));
                    }
                }
            }
        });

        wlColPerson.setCellValueFactory(new PropertyValueFactory<>("lastCommitAuthor"));
        wlColPerson.setCellFactory(param -> new TableCell<>() {
            @Override
            protected void updateItem(PersonIdent item, boolean empty) {
                if (empty) {
                    setText(null);
                } else {
                    if (item != null) {
                        setText(item.getName());
                        setTextFill(CommitView.getUserColor(item));
                    } else {
                        setText(ResourceStore.getString("watchlist.author.placeholder"));
                        setTextFill(Color.BLACK);
                    }
                }
            }
        });
    }

    /**
     * Allow the user to drag & drop repository folders from OS explorer
     */
    private void setupDragAndDropRepoAdd() {
        // indicate link opportunity upon drag over (with files)
        tblWatchlist.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK);
                event.consume();
            }
        });

        // try to add repo to watchlist upon drop
        tblWatchlist.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                addRepoToWatchlist(db.getFiles().get(0));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Setup branch combo box and action when selection is changed.
     */
    private void setupSwitchBranch() {
        cbBoxBranch.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            try {
                RepositoryInformation repo = tblWatchlist.getSelectionModel().getSelectedItem();
                if (oldValue != null && repo != null && newValue != null) {
                    // if remote branch was selected, create it locally before checkout
                    if (newValue.isRemoteOnly()) {
                        gitManager.createBranch(repo, newValue.getShortName());
                        gitManager.checkout(repo, newValue.getShortName());
                        updateBranches(repo);
                    } else {
                        gitManager.checkout(repo, newValue.getShortName());
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

    /**
     * Setup commit log update upon watchlist selection change
     */
    private void setupCommitLogDisplay() {
        tblWatchlist.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            updateBranches(newValue);
            updateCommitLog(newValue);
            // clear "New"-icon when deselecting
            if (oldValue != null && newValue != null) {
                fileManager.setNewChanges(oldValue.getID(), 0);
            }
        });
    }

    /**
     * Update branch selection ComboBox according to given repo
     * @param repo Repo to update branches for.
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

    /**
     * Update commit log for {@code repo}
     * @param repo Repo to update commit log for.
     */
    private void updateCommitLog(RepositoryInformation repo) {
        if (repo != null) {
            gitManager.getLogAsync(repo, (success, changes, ex) -> Platform.runLater(() -> {
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

    /**
     * Update status of all repo on watchlist.
     * @param actionEvent
     */
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

    /**
     * Called when watchlist or status of a repo on the watchlist changes.
     * @param e Change event
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        Platform.runLater(() -> {
            if (e.getPropertyName().equals("watchlist")) {
                setWatchlistDisplay((Collection<RepositoryInformation>)e.getNewValue());
            } else if (e.getPropertyName().equals("repoStatus")) {
                tblWatchlist.refresh();
            }
        });
    }

    /**
     * Set watchlist items and sort items.
     * @param repoInfo New Watchlist items
     */
    private synchronized void setWatchlistDisplay(Collection<RepositoryInformation> repoInfo) {
        // remember sort order
        TableColumn<RepositoryInformation, ?> sortCol = null;
        if (!tblWatchlist.getSortOrder().isEmpty()) {
            sortCol = tblWatchlist.getSortOrder().get(0);
        }
        tblWatchlist.setItems(FXCollections.observableArrayList(repoInfo));
        tblWatchlist.refresh();
        if (sortCol != null) {
            tblWatchlist.getSortOrder().add(sortCol);
        }
    }

    /**
     * Pull all repos on watchlist.
     * @param actionEvent Event
     */
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

    /**
     * Called when a pull operation is finished.
     * Used to update commit log.
     * @param repo Repo for which pull was executed
     * @param status Status of pull
     */
    @Override
    public void pullExecuted(RepositoryInformation repo, MergeResult.MergeStatus status) {
        RepositoryInformation selectedItem = tblWatchlist.getSelectionModel().getSelectedItem();
        tblWatchlist.refresh();
        if (selectedItem != null && selectedItem.getPath().equals(repo.getPath())) {
            updateCommitLog(selectedItem);
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

    /**
     * Set status bar and clear pull results.
     * @param status
     */
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
            Logger.getAnonymousLogger().severe(ex.getMessage());
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void btnEditAuthClicked(ActionEvent actionEvent) throws IOException {
        ControllerAuthList.openWindow();
    }

    @FXML
    public void onBtnAddToWatchlistClicked(ActionEvent actionEvent) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(ResourceStore.getString("main.select_repo_path_to_add.title"));
        addRepoToWatchlist(dirChooser.showDialog(lblStatus.getScene().getWindow()));
    }

    /**
     * Attempt to add repo to watchlist.
     * Repo path must be valid.
     * @param newRepoFolder Repo to attempt to add.
     */
    private void addRepoToWatchlist(File newRepoFolder) {
        try {
            if (newRepoFolder != null) {
                if (!Utils.validateRepositoryPath(newRepoFolder.getAbsolutePath())) {
                    throw new IllegalArgumentException();
                }
                fileManager.addToWatchlist(new RepositoryInformation(newRepoFolder.getAbsolutePath()));
            }
        } catch (IllegalArgumentException ex) {
            showError(ResourceStore.getString("main.invalid_repo_path_selected.header"),
                    ResourceStore.getString("main.invalid_repo_path_selected.message", newRepoFolder.getAbsolutePath()));
        }
    }

    /**
     * Called when a required program file becomes unavailable (e.g. due to USB stick disconnect)
     * @param path File which became unavailable
     */
    @Override
    public void fileUnavailable(File path) {
        // show error and exit after user has closed error message
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            ((Stage) a.getDialogPane().getScene().getWindow()).getIcons().add(ResourceStore.getImage("icon_app.png"));
            a.setTitle(ResourceStore.getString("errordialog.title"));
            a.setHeaderText(ResourceStore.getString("main.file_unavailable.header"));
            a.setContentText(ResourceStore.getString("main.file_unavailable.content", path.getAbsolutePath()));
            a.showAndWait();
            System.exit(-1);
        });

    }
}
