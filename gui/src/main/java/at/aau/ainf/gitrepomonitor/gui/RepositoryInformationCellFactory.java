package at.aau.ainf.gitrepomonitor.gui;

import at.aau.ainf.gitrepomonitor.files.FileManager;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

import java.io.IOException;

public class RepositoryInformationCellFactory implements Callback<ListView<RepositoryInformation>, ListCell<RepositoryInformation>> {

    @Override
    public ListCell<RepositoryInformation> call(ListView<RepositoryInformation> param) {
        ListCell<RepositoryInformation> cell = new ListCell<>() {
            @Override
            protected void updateItem(RepositoryInformation item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        };

        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem();
        editItem.setText("Edit");
        editItem.setOnAction(event -> {
            try {
                openEditWindow(cell.getItem());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        MenuItem deleteItem = new MenuItem();
        deleteItem.setText("Remove");
        deleteItem.setOnAction(event -> FileManager.getInstance().deleteRepo(cell.getItem()));
        contextMenu.getItems().addAll(editItem, deleteItem);

        cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(contextMenu);
            }
        });
        return cell;
    }

    private void openEditWindow(RepositoryInformation repo) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/editrepo/edit_repo.fxml"),
                ResourceStore.getResourceBundle());
        Parent root = loader.load();
        ((ControllerEditRepo)loader.getController()).setRepo(repo);     // set repo information to display

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("Edit Repository Information");
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.show();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());
    }
}
