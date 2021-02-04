package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.util.Callback;

import java.io.IOException;

/**
 * Custom cell factory for authentication credentials.
 * Includes custom context menu.
 */
public class AuthInfoCellFactory implements
        Callback<ListView<AuthenticationCredentials>, ListCell<AuthenticationCredentials>> {

    @Override
    public ListCell<AuthenticationCredentials> call(ListView<AuthenticationCredentials> listView) {
        ListCell<AuthenticationCredentials> cell = new AuthInfoListViewCell();

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
            }
        });

        return cell;
    }

    private static void openEditAuthInfoWindow(AuthenticationCredentials authInfo) {
        try {
            ControllerEditAuth.openWindow(authInfo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class AuthInfoListViewCell extends ListCell<AuthenticationCredentials> {
        @Override
        protected void updateItem(AuthenticationCredentials item, boolean empty) {
            super.updateItem(item, empty);

            // clear entry if no/empty item (required for clean refresh of listview)
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item.getName());
            }
        }
    }

    /**
     * Custom context menu for auth credentials.
     * Menu Items: Edit, Remove
     */
    static class AuthInfoContextMenu extends ContextMenu implements AlertDisplay {
        private final AuthenticationCredentials item;

        public AuthInfoContextMenu(ListCell<AuthenticationCredentials> cell) {
            this.item = cell.getItem();
            setupMenuItems();
        }

        private void setupMenuItems() {
            MenuItem editItem = new MenuItem();
            editItem.setText(ResourceStore.getString("ctxmenu.edit"));
            editItem.setGraphic(getCtxMenuIcon("icon_edit.png"));
            editItem.setOnAction(event -> openEditAuthInfoWindow(item));

            MenuItem deleteItem = new MenuItem();
            deleteItem.setText(ResourceStore.getString("ctxmenu.remove"));
            deleteItem.setGraphic(getCtxMenuIcon("icon_delete.png"));
            deleteItem.setOnAction(event -> {
                int affectedRepoCount = FileManager.getInstance().getUsingRepoCount(item.getID());
                // show warning if repos are using this auth credentials entry
                if (affectedRepoCount > 0) {
                    if (showConfirmationDialog(Alert.AlertType.WARNING,
                            ResourceStore.getString("auth_list.confirm_delete.title"),
                            ResourceStore.getString("auth_list.confirm_delete.header", affectedRepoCount),
                            ResourceStore.getString("auth_list.confirm_delete.content"))) {

                        SecureStorage.getImplementation().delete(item.getID());
                    }
                } else {
                    // show confirmation dialog
                    if (showConfirmationDialog(Alert.AlertType.INFORMATION,
                            ResourceStore.getString("auth_list.confirm_delete.title"),
                            ResourceStore.getString("auth_list.confirm_delete.header_no_affected_repos"),
                            ResourceStore.getString("auth_list.confirm_delete.content"))) {

                        SecureStorage.getImplementation().delete(item.getID());
                    }
                }
            });

            this.getItems().addAll(editItem, deleteItem);
        }

        /**
         * Get icon adapted for context menu.
         * @param iconName Icon name (without path)
         * @return Icon adapted for context menu
         */
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
