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
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
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

    private final GitManager gitManager;
    private final RepositoryInformation item;
    private final StatusDisplay statusDisplay;
    private final ProgressMonitor progressMonitor;
    private final SecureStorage secureStorage;

    public RepositoryInformationContextMenu(ListCell<RepositoryInformation> cell, StatusDisplay statusDisplay, ProgressMonitor progressMonitor) {
        this.secureStorage = SecureStorage.getImplementation();
        this.gitManager = GitManager.getInstance();
        this.item = cell.getItem();
        this.statusDisplay = statusDisplay;
        this.progressMonitor = progressMonitor;
        setupMenuItems();
    }

    private void setupMenuItems() {
        MenuItem checkStatusItem = new MenuItem();
        checkStatusItem.setText(ResourceStore.getString("ctxmenu.check_status"));
        checkStatusItem.setGraphic(getCtxMenuIcon("icon_update_status.png"));
        checkStatusItem.setOnAction(event -> {
            String masterPW = null;
            if (item.isAuthenticated() && !secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
                // abort if input dialog was cancelled
                if (masterPW == null) {
                    return;
                }
            }

            setStatus(ResourceStore.getString("status.update_repo_status"));
            gitManager.updateRepoStatusAsync(item, Utils.toCharOrNull(masterPW), (success, reposChecked, reposFailed, ex) -> {
                if (!success) {
                    setStatus(ResourceStore.getString("status.wrong_master_password"));
                    showError(ResourceStore.getString("status.wrong_master_password"));
                } else {
                    setStatus(ResourceStore.getString("status.updated_n_repo_status", reposChecked));
                }
            });
        });

        MenuItem pullItem = new MenuItem();
        pullItem.setText(ResourceStore.getString("ctxmenu.pull"));
        pullItem.setGraphic(getCtxMenuIcon("icon_pull.png"));
        pullItem.setOnAction(event -> {
            String masterPW = null;
            if (item.isAuthenticated() && !secureStorage.isMasterPasswordCached()) {
                masterPW = showMasterPasswordInputDialog(false);
                // abort if input dialog was cancelled
                if (masterPW == null) {
                    return;
                }
            }
            gitManager.pullRepoAsync(item, Utils.toCharOrNull(masterPW), (results, pullsSuccess,
                                                                                    pullsFailed, wrongMP) -> {
                if (wrongMP) {
                    setStatus(ResourceStore.getString("status.wrong_master_password"));
                    showError(ResourceStore.getString("status.wrong_master_password"));
                } else if (pullsFailed == 1 && results.isEmpty()) {
                    setStatus(ResourceStore.getString("status.repo.not_accessible"));
                } else {
                    PullCallback.PullResult result = results.get(0);
                    String statusMsg = getStatusMessage(result.getStatus());
                    if (statusMsg != null) {
                        setStatus(statusMsg);
                    } else if (result.getEx() instanceof InvalidConfigurationException) {
                        showError(ResourceStore.getString("status.repo.no_remote"));
                    }
                }
            }, progressMonitor);
        });

        MenuItem editItem = new MenuItem();
        editItem.setText(ResourceStore.getString("ctxmenu.edit"));
        editItem.setGraphic(getCtxMenuIcon("icon_edit.png"));
        editItem.setOnAction(event -> {
            try {
                ControllerEditRepo.openWindow(item);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        MenuItem deleteItem = new MenuItem();
        deleteItem.setText(ResourceStore.getString("ctxmenu.remove"));
        deleteItem.setGraphic(getCtxMenuIcon("icon_delete.png"));
        deleteItem.setOnAction(event -> FileManager.getInstance().deleteRepo(item));

        MenuItem showInExplorerItem = new MenuItem();
        showInExplorerItem.setText(ResourceStore.getString("ctxmenu.show_in_explorer"));
        showInExplorerItem.setGraphic(getCtxMenuIcon("icon_explorer.png"));
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
        } else if(status == MergeResult.MergeStatus.CHECKOUT_CONFLICT) {
            statusMsg = ResourceStore.getString("status.pull_checkout_conflict");
        } else if(status == MergeResult.MergeStatus.FAILED) {
            statusMsg = ResourceStore.getString("status.pull_failed");
        }
        return statusMsg;
    }

    private void setStatus(String status) {
        if (statusDisplay != null) {
            statusDisplay.displayStatus(status);
        }
    }

    private ImageView getCtxMenuIcon(String iconName) {
        ImageView icon = new ImageView(ResourceStore.getImage(iconName));
        icon.setFitWidth(18);
        icon.setFitHeight(18);
        // make icons black
        ColorAdjust colorAdjust = new ColorAdjust();
        colorAdjust.setBrightness(-1);
        icon.setEffect(colorAdjust);
        return icon;
    }
}