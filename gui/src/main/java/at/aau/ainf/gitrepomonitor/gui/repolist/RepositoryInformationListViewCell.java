package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class RepositoryInformationListViewCell extends ListCell<RepositoryInformation> {
    @FXML
    private Label lblName;
    @FXML
    private HBox container;
    @FXML
    private ImageView iconAttention;
    @FXML
    private Label lblIcon;

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
            setIcon(item);
            setGraphic(container);
        }
    }

    private void setIcon(RepositoryInformation item) {
        iconAttention.setVisible(true);
        if (!item.isPathValid()) {
            iconAttention.setImage(getImage("icon_attention.png"));
            lblIcon.setTooltip(new Tooltip("Invalid path"));
        } else if (!item.isUpToDate()) {
            iconAttention.setImage(getImage("icon_pull.png"));
            lblIcon.setTooltip(new Tooltip("Update available"));
        } else if (!item.hasRemote()) {
            iconAttention.setImage(getImage("icon_attention.png"));
            lblIcon.setTooltip(new Tooltip("Repository has no remote"));
        } else if (!item.isRemoteAccessible()) {
            iconAttention.setImage(getImage("icon_attention.png"));
            lblIcon.setTooltip(new Tooltip("No authentication information provided"));
        } else {
            iconAttention.setVisible(false);
            lblIcon.setTooltip(null);
        }
    }

    private Image getImage(String path) {
        return new Image("/at/aau/ainf/gitrepomonitor/gui/icons/" + path);
    }
}
