package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.*;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Pair;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class RepositoryInformationContextMenu extends ContextMenu implements ErrorDisplay, MasterPasswordQuery {

    private GitManager gitManager;
    private RepositoryInformation item;
    private StatusDisplay statusDisplay;
    private ProgressMonitor progressMonitor;

    public RepositoryInformationContextMenu(ListCell<RepositoryInformation> cell, StatusDisplay statusDisplay, ProgressMonitor progressMonitor) {
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
            if (item.isAuthenticated()) {
                masterPW = showMasterPasswordInputDialog(false);
            }

            setStatus(ResourceStore.getString("status.update_repo_status"));
            gitManager.updateRepoStatusAsync(item.getPath(), masterPW, (success, reposChecked, reposFailed, ex) -> {
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
            // TODO: use stored credentials when implemented
            gitManager.pullRepoAsync(item.getPath(), (results) -> {
                String statusMsg = getStatusMessage(results.get(0).getStatus());
                if (statusMsg != null) {
                    setStatus(statusMsg);
                } else {
                    if (results.get(0).getEx() instanceof TransportException) {
                        Platform.runLater(() -> {
                            LoginDialog loginDialog = new LoginDialog(item.toString());
                            Optional<Pair<Pair<String, String>, Boolean>> credentials = loginDialog.showAndWait();

                            credentials.ifPresent(pairBooleanPair -> gitManager.pullRepoAsync(item.getPath(),
                                    pairBooleanPair.getKey().getKey(),
                                    pairBooleanPair.getKey().getValue(), (results1) -> {
                                        String statusMsg1 = getStatusMessage(results1.get(0).getStatus());
                                        if (statusMsg1 != null) {
                                            setStatus(statusMsg1);
                                        } else {
                                            showError(results.get(0).getEx().getMessage());
                                        }
                                    }));
                        });
                    } else {
                        showError(results.get(0).getEx().getMessage());
                    }
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