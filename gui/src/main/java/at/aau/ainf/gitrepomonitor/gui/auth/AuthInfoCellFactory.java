package at.aau.ainf.gitrepomonitor.gui.auth;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.util.Callback;

import java.io.IOException;

public class AuthInfoCellFactory implements
        Callback<ListView<AuthenticationInformation>, ListCell<AuthenticationInformation>> {

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

    static class AuthInfoContextMenu extends ContextMenu implements AlertDisplay {
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
            deleteItem.setOnAction(event -> {
                int affectedRepoCount = FileManager.getInstance().getUsingRepoCount(item.getID());
                if (affectedRepoCount > 0) {
                    if (showConfirmationDialog(Alert.AlertType.WARNING,
                            ResourceStore.getString("auth_list.confirm_delete.title"),
                            ResourceStore.getString("auth_list.confirm_delete.header", affectedRepoCount),
                            ResourceStore.getString("auth_list.confirm_delete.content"))) {

                        SecureStorage.getImplementation().delete(item.getID());
                    }
                } else {
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
