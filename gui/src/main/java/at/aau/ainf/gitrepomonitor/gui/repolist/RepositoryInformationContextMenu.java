package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
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
    private ListCell<RepositoryInformation> cell;
    private StatusDisplay statusDisplay;

    public RepositoryInformationContextMenu(ListCell<RepositoryInformation> cell, StatusDisplay statusDisplay) {
        this.gitManager = GitManager.getInstance();
        this.cell = cell;
        this.statusDisplay = statusDisplay;
        setupMenuItems();
    }

    private void setupMenuItems() {
        MenuItem checkStatusItem = new MenuItem();
        checkStatusItem.setText("Check Status");
        checkStatusItem.setOnAction(event -> {
            gitManager.updateRepoStatusAsync(cell.getItem().getPath(), (success, ex) -> {
                if (!success) {
                    setStatus(ex.getMessage());
                }
            });
        });

        MenuItem pullItem = new MenuItem();
        pullItem.setText("Pull");
        pullItem.setOnAction(event -> {
            // TODO: use stored credentials when implemented
            gitManager.pullRepoAsync(cell.getItem().getPath(), (success, ex) -> {
                if (success) {
                    setStatus("Pull successful");
                } else {
                    if (ex instanceof TransportException) {
                        Platform.runLater(() -> {
                            LoginDialog loginDialog = new LoginDialog(cell.getItem().toString());
                            Optional<Pair<Pair<String, String>, Boolean>> credentials = loginDialog.showAndWait();

                            credentials.ifPresent(pairBooleanPair -> gitManager.pullRepoAsync(cell.getItem().getPath(),
                                    pairBooleanPair.getKey().getKey(),
                                    pairBooleanPair.getKey().getValue(), (success1, ex1) -> {
                                        if (success1) {
                                            setStatus("Pull successful");
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
        editItem.setText(ResourceStore.getResourceBundle().getString("ctxmenu.edit"));
        editItem.setOnAction(event -> {
            try {
                openEditWindow(cell.getItem());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        MenuItem deleteItem = new MenuItem();
        deleteItem.setText(ResourceStore.getResourceBundle().getString("ctxmenu.remove"));
        deleteItem.setOnAction(event -> FileManager.getInstance().deleteRepo(cell.getItem()));

        MenuItem showInExplorerItem = new MenuItem();
        showInExplorerItem.setText(ResourceStore.getResourceBundle().getString("ctxmenu.show_in_explorer"));
        showInExplorerItem.setOnAction(event -> {
            try {
                Desktop.getDesktop().open(new File(cell.getItem().getPath()));
            } catch (Exception e) {
                showError(ResourceStore.getResourceBundle().getString("errormsg.open_in_explorer_failed"));
            }
        });

        this.getItems().addAll(checkStatusItem, pullItem, editItem, deleteItem, showInExplorerItem);
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
        stage.setTitle(ResourceStore.getResourceBundle().getString("edit_repo"));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }
}
