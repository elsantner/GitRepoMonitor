package at.aau.ainf.gitrepomonitor.gui;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Pair;
import org.eclipse.jgit.api.errors.TransportException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class RepositoryInformationCellFactory
        implements ErrorDisplay, Callback<ListView<RepositoryInformation>, ListCell<RepositoryInformation>> {

    private StatusDisplay statusDisplay;
    private GitManager gitManager;

    public RepositoryInformationCellFactory() {
        this.gitManager = GitManager.getInstance();
    }

    public RepositoryInformationCellFactory(StatusDisplay statusDisplay) {
        this();
        this.statusDisplay = statusDisplay;
    }

    @Override
    public ListCell<RepositoryInformation> call(ListView<RepositoryInformation> listView) {
        ListCell<RepositoryInformation> cell = new RepositoryInformationListViewCell();

        // ctx menu only on non-empty list entries
        cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(getContextMenu(cell));
            }
        });
        cell.prefWidthProperty().bind(listView.prefWidthProperty());
        return cell;
    }

    /**
     * Returns a setup & configured ContextMenu for the given cell
     * @param cell The cell for which the context menu is created
     * @return setup ContextMenu
     */
    private ContextMenu getContextMenu(ListCell<RepositoryInformation> cell) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem pullItem = new MenuItem();
        pullItem.setText("Pull");
        pullItem.setOnAction(event -> {
            // TODO: use stored credentials when implemented
            gitManager.pullRepoAsync(cell.getItem().getPath(), (success, ex) -> {
                if (success) {
                    setStatusPullSuccess();
                } else {
                    if (ex instanceof TransportException) {
                        Platform.runLater(() -> {
                            LoginDialog loginDialog = new LoginDialog(cell.getItem().toString());
                            Optional<Pair<Pair<String, String>, Boolean>> credentials = loginDialog.showAndWait();

                            credentials.ifPresent(pairBooleanPair -> gitManager.pullRepoAsync(cell.getItem().getPath(),
                                    pairBooleanPair.getKey().getKey(),
                                    pairBooleanPair.getKey().getValue(), (success1, ex1) -> {
                                        if (success1) {
                                            setStatusPullSuccess();
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

        contextMenu.getItems().addAll(pullItem, editItem, deleteItem, showInExplorerItem);
        return contextMenu;
    }

    private void setStatusPullSuccess() {
        if (statusDisplay != null) {
            statusDisplay.displayStatus("Pull successful");
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

    private static class RepositoryInformationListViewCell extends ListCell<RepositoryInformation> {
        @FXML
        private Label lblName;
        @FXML
        private HBox container;
        @FXML
        private ImageView iconAttention;

        private FXMLLoader loader;

        @Override
        protected void updateItem(RepositoryInformation item, boolean empty) {
            super.updateItem(item, empty);

            // clear entry if no/empty item (required for clean refresh of listview)
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                // load fxml if not loaded already
                if (loader == null) {
                    loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/repo_list_item.fxml"));
                    loader.setController(this);

                    try {
                        loader.load();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // fill display elements with data
                lblName.setText(item.toString());
                iconAttention.setVisible(!item.isPathValid());
                setGraphic(container);
            }
        }
    }
}
