package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.core.git.PullCallback;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.LoginDialog;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
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
import org.eclipse.jgit.api.errors.TransportException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class RepositoryInformationContextMenu extends ContextMenu implements ErrorDisplay {

    private GitManager gitManager;
    private RepositoryInformation item;
    private StatusDisplay statusDisplay;

    public RepositoryInformationContextMenu(ListCell<RepositoryInformation> cell, StatusDisplay statusDisplay) {
        this.gitManager = GitManager.getInstance();
        this.item = cell.getItem();
        this.statusDisplay = statusDisplay;
        setupMenuItems();
    }

    private void setupMenuItems() {
        MenuItem checkStatusItem = new MenuItem();
        checkStatusItem.setText(ResourceStore.getString("ctxmenu.check_status"));
        checkStatusItem.setOnAction(event -> {
            setStatus(ResourceStore.getString("status.update_repo_status"));
            gitManager.updateRepoStatusAsync(item.getPath(), (success, reposChecked, ex) -> {
                if (!success) {
                    setStatus(ex.getMessage());
                } else {
                    setStatus(ResourceStore.getString("status.updated_n_repo_status", reposChecked));
                }
            });
        });

        MenuItem pullItem = new MenuItem();
        pullItem.setText(ResourceStore.getString("ctxmenu.pull"));
        pullItem.setOnAction(event -> {
            // TODO: use stored credentials when implemented
            gitManager.pullRepoAsync(item.getPath(), (success, status, ex) -> {
                String statusMsg = getStatusMessage(success, status);
                if (statusMsg != null) {
                    setStatus(statusMsg);
                } else {
                    if (ex instanceof TransportException) {
                        Platform.runLater(() -> {
                            LoginDialog loginDialog = new LoginDialog(item.toString());
                            Optional<Pair<Pair<String, String>, Boolean>> credentials = loginDialog.showAndWait();

                            credentials.ifPresent(pairBooleanPair -> gitManager.pullRepoAsync(item.getPath(),
                                    pairBooleanPair.getKey().getKey(),
                                    pairBooleanPair.getKey().getValue(), (success1, status1, ex1) -> {
                                        String statusMsg1 = getStatusMessage(success1, status1);
                                        if (statusMsg1 != null) {
                                            setStatus(statusMsg1);
                                        } else {
                                            showError(ex1.getMessage());
                                        }
                                    }));
                        });
                    } else {
                        showError(ex.getMessage());
                    }
                }
            });
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
                System.out.println(item);
                Desktop.getDesktop().open(new File(item.getPath()));
            } catch (Exception e) {
                showError(ResourceStore.getString("errormsg.open_in_explorer_failed"));
            }
        });

        if (!item.isPathValid()) {
            pullItem.setDisable(true);
        }

        this.getItems().addAll(checkStatusItem, pullItem, editItem, deleteItem, showInExplorerItem);
    }

    private String getStatusMessage(boolean success, PullCallback.Status status) {
        String statusMsg = null;
        if (success && status != PullCallback.Status.ALREADY_UP_TO_DATE) {
            statusMsg = ResourceStore.getString("status.pull_successful");
        } else if(status == PullCallback.Status.ALREADY_UP_TO_DATE) {
            statusMsg = ResourceStore.getString("status.pull_no_changes");
        } else if(status == PullCallback.Status.CONFLICTING) {
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