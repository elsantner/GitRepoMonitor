package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.git.PullCallback;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.settings.ControllerSettings;
import com.sun.javafx.collections.ImmutableObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ControllerPullResults implements Initializable {

    @FXML
    private ListView<PullCallback.PullResult> listResults;

    private FileManager fileManager;

    public static FXMLLoader getLoader() {
        return new FXMLLoader(ControllerSettings.class.getResource("/at/aau/ainf/gitrepomonitor/gui/main/pull_result_list.fxml"),
                ResourceStore.getResourceBundle());
    }

    public void setDisplay(List<PullCallback.PullResult> results) {
        listResults.setItems(new ImmutableObservableList<>(results.toArray(new PullCallback.PullResult[0])));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        listResults.setCellFactory(param -> new PullResultListViewCell());
        fileManager = FileManager.getInstance();
    }

    private class PullResultListViewCell extends ListCell<PullCallback.PullResult> {

        @FXML
        private HBox container;
        @FXML
        private Label lblName;
        @FXML
        private ImageView imgIcon;

        private FXMLLoader loader;

        @Override
        protected void updateItem(PullCallback.PullResult item, boolean empty) {
            super.updateItem(item, empty);

            // clear entry if no/empty item (required for clean refresh of listview)
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                // load fxml if not loaded already
                if (loader == null) {
                    loader = new FXMLLoader(
                            getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/pull_result_list_item.fxml"),
                            ResourceStore.getResourceBundle()
                    );
                    loader.setController(this);

                    try {
                        loader.load();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // fill display elements with data
                String txt = item.getStatus() + " '" +
                        fileManager.getRepo(item.getRepoPath()).toString() + "'";
                if (item.getEx() != null) {
                    txt += ": " + item.getEx().getLocalizedMessage();
                }

                lblName.setText(txt);
                setIcon(item);
                setGraphic(container);
            }
        }

        private void setIcon(PullCallback.PullResult item) {
            if (item.getStatus().isSuccessful()) {
                imgIcon.setImage(ResourceStore.getImage("icon_check.png"));
            } else {
                imgIcon.setImage(ResourceStore.getImage("icon_attention.png"));
            }
        }
    }
}
