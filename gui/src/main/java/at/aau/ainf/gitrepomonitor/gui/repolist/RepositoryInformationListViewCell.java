package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
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
        String imgPath = null;
        String tooltipKey = null;
        switch (item.getStatus()) {
            case PATH_INVALID:
                imgPath = "icon_attention.png";
                tooltipKey = "status.repo.invalid_path";
                break;
            case PULL_AVAILABLE:
                imgPath = "icon_pull.png";
                tooltipKey = "status.repo.pull_available";
                break;
            case PUSH_AVAILABLE:
                imgPath = "icon_push.png";
                tooltipKey = "status.repo.push_available";
                break;
            case NO_REMOTE:
                imgPath = "icon_attention.png";
                tooltipKey = "status.repo.no_remote";
                break;
            case INACCESSIBLE_REMOTE:
                imgPath = "icon_attention.png";
                tooltipKey = "status.repo.no_auth_info";
                break;
            case MERGE_NEEDED:
                imgPath = "icon_merge.png";
                tooltipKey = "status.repo.merge_required";
                break;
        }
        if (imgPath != null) {
            iconAttention.setImage(getImage(imgPath));
            lblIcon.setTooltip(new Tooltip(ResourceStore.getString(tooltipKey)));
        } else {
            iconAttention.setVisible(false);
            lblIcon.setTooltip(null);
        }
    }

    private Image getImage(String path) {
        return new Image("/at/aau/ainf/gitrepomonitor/gui/icons/" + path);
    }
}
