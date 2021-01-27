package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.core.git.PullCallback;
import at.aau.ainf.gitrepomonitor.gui.ErrorDisplay;
import at.aau.ainf.gitrepomonitor.gui.MasterPasswordQuery;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationContextMenu;
import at.aau.ainf.gitrepomonitor.gui.repolist.RepositoryInformationListViewCell;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.util.Callback;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class AuthInfoCellFactory implements
        Callback<ListView<AuthenticationInformation>, ListCell<AuthenticationInformation>> {

    private Control keyListener;

    /**
     * Create cell factory for authentication information
     * @param keyListener provides key-events for list cells
     */
    public AuthInfoCellFactory(Control keyListener) {
        super();
        this.keyListener = keyListener;
    }

    @Override
    public ListCell<AuthenticationInformation> call(ListView<AuthenticationInformation> listView) {
        ListCell<AuthenticationInformation> cell = new AuthInfoListViewCell();

        // ctx menu only on non-empty list entries
        cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                cell.setOnMouseClicked(null);
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(new AuthInfoContextMenu(cell));
                // open edit repo window on double click or enter-press
                cell.setOnMouseClicked(mouseClickedEvent -> {
                    if (mouseClickedEvent.getButton().equals(MouseButton.PRIMARY) &&
                            mouseClickedEvent.getClickCount() == 2) {
                        openEditAuthInfoWindow(cell.getItem());
                    }
                });
                keyListener.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        openEditAuthInfoWindow(cell.getItem());
                    }
                });
            }
        });

        return cell;
    }

    private static void openEditAuthInfoWindow(AuthenticationInformation authInfo) {
        try {
            ControllerEditAuth.openWindow(authInfo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class AuthInfoListViewCell extends ListCell<AuthenticationInformation> {
        @Override
        protected void updateItem(AuthenticationInformation item, boolean empty) {
            super.updateItem(item, empty);

            // clear entry if no/empty item (required for clean refresh of listview)
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item.getName());
            }
        }
    }

    static class AuthInfoContextMenu extends ContextMenu {
        private final AuthenticationInformation item;

        public AuthInfoContextMenu(ListCell<AuthenticationInformation> cell) {
            this.item = cell.getItem();
            setupMenuItems();
        }

        private void setupMenuItems() {
            MenuItem editItem = new MenuItem();
            editItem.setText(ResourceStore.getString("ctxmenu.edit"));
            editItem.setGraphic(getCtxMenuIcon("icon_edit.png"));
            editItem.setOnAction(event -> {
                openEditAuthInfoWindow(item);
            });

            MenuItem deleteItem = new MenuItem();
            deleteItem.setText(ResourceStore.getString("ctxmenu.remove"));
            deleteItem.setGraphic(getCtxMenuIcon("icon_delete.png"));
            deleteItem.setOnAction(event -> SecureStorage.getImplementation().delete(item.getID()));

            this.getItems().addAll(editItem, deleteItem);
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
}
