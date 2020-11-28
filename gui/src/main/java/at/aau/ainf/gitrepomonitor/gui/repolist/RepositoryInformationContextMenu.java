package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.core.git.PullCallback;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class RepositoryInformationContextMenu extends ContextMenu implements ErrorDisplay, MasterPasswordQuery {

    private GitManager gitManager;
    private RepositoryInformation item;
    private StatusDisplay statusDisplay;
    private ProgressMonitor progressMonitor;
    private SecureStorage secureStorage;

    public RepositoryInformationContextMenu(ListCell<RepositoryInformation> cell, StatusDisplay statusDisplay, ProgressMonitor progressMonitor) {
        this.secureStorage = SecureStorage.getInstance();
        this.gitManager = GitManager.getInstance();
        this.item = cell.getItem();
        this.statusDisplay = statusDisplay;
        this.progressMonitor = progressMonitor;
        setupMenuItems();
    }

    private void setupMenuItems() {
        MenuItem checkStatusItem = new MenuItem();
        checkStatusItem.setText(ResourceStore.getString("ctxmenu.check_status"));
        checkStatusItem.setOnAction(event -> {
            String masterPW = null;
            if (item.isAuthenticated() && !secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
            }

            setStatus(ResourceStore.getString("status.update_repo_status"));
            gitManager.updateRepoStatusAsync(item.getPath(), Utils.toCharOrNull(masterPW), (success, reposChecked, reposFailed, ex) -> {
                if (!success) {
                    setStatus(ResourceStore.getString("status.wrong_master_password", reposChecked));
                } else {
                    setStatus(ResourceStore.getString("status.updated_n_repo_status", reposChecked));
                }
            });
        });

        MenuItem pullItem = new MenuItem();
        pullItem.setText(ResourceStore.getString("ctxmenu.pull"));
        pullItem.setOnAction(event -> {
            String masterPW = null;
            if (item.isAuthenticated() && !secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
            }
            gitManager.pullRepoAsync(item.getPath(), Utils.toCharOrNull(masterPW), (results, pullsFailed, wrongMP) -> {
                PullCallback.PullResult result = results.get(0);
                String statusMsg = getStatusMessage(result.getStatus());
                if (statusMsg != null) {
                    setStatus(statusMsg);
                } else if (wrongMP) {
                    showError("Wrong Master Password");
                } else if (result.getEx() instanceof InvalidConfigurationException) {
                    showError("Repository has no remote");
                }
            }, progressMonitor);
        });

        MenuItem editItem = new MenuItem();
        editItem.setText(ResourceStore.getString("ctxmenu.edit"));
        editItem.setOnAction(event -> {
            try {
                openEditWindow(item);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        MenuItem deleteItem = new MenuItem();
        deleteItem.setText(ResourceStore.getString("ctxmenu.remove"));
        deleteItem.setOnAction(event -> FileManager.getInstance().deleteRepo(item));

        MenuItem showInExplorerItem = new MenuItem();
        showInExplorerItem.setText(ResourceStore.getString("ctxmenu.show_in_explorer"));
        showInExplorerItem.setOnAction(event -> {
            try {
                Desktop.getDesktop().open(new File(item.getPath()));
            } catch (Exception e) {
                showError(ResourceStore.getString("errormsg.open_in_explorer_failed"));
            }
        });

        if (item.getStatus() == RepositoryInformation.RepoStatus.PATH_INVALID) {
            pullItem.setDisable(true);
        }

        this.getItems().addAll(checkStatusItem, pullItem, editItem, deleteItem, showInExplorerItem);
    }

    private String getStatusMessage(MergeResult.MergeStatus status) {
        String statusMsg = null;
        if (status.isSuccessful() && status != MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
            statusMsg = ResourceStore.getString("status.pull_successful");
        } else if(status == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) {
            statusMsg = ResourceStore.getString("status.pull_no_changes");
        } else if(status == MergeResult.MergeStatus.CONFLICTING) {
            statusMsg = ResourceStore.getString("status.pull_conflict");
        }
        return statusMsg;
    }

    private void setStatus(String status) {
        if (statusDisplay != null) {
            statusDisplay.displayStatus(status);
        }
    }

    private void openEditWindow(RepositoryInformation repo) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/editrepo/edit_repo.fxml"),
                ResourceStore.getResourceBundle());
        Parent root = loader.load();
        ((ControllerEditRepo)loader.getController()).setRepo(repo);     // set repo information to display

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle(ResourceStore.getString("edit_repo"));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }
}