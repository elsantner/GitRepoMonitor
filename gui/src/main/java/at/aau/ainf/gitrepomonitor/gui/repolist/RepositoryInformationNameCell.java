package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.io.IOException;

public class RepositoryInformationNameCell extends TableCell<RepositoryInformation, RepositoryInformation> {

    @FXML
    private Label lblName;
    @FXML
    private HBox container;
    @FXML
    private ImageView iconAttention;
    @FXML
    private Label lblIcon;
    @FXML
    private Label lblNewChange;

    private FXMLLoader loader;
    private FileManager fileManager;

    /**
     * Create table cell.
     */
    public RepositoryInformationNameCell() {
        this.fileManager = FileManager.getInstance();
    }

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
                loader = new FXMLLoader(
                        getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/repo_list_item.fxml"),
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
            lblName.setTextOverrun(OverrunStyle.CENTER_WORD_ELLIPSIS);
            lblName.setText(item.toString());
            setIcon(item);
            setNewChange(item);
            setGraphic(container);
        }
    }

    private void setNewChange(RepositoryInformation item) {
        lblNewChange.setVisible(item.hasNewChanges());
    }

    private void setIcon(RepositoryInformation item) {
        iconAttention.setVisible(true);
        String imgPath = null;
        String tooltipKey = null;
        switch (item.getStatus()) {
            case PATH_INVALID:
                imgPath = "icon_missing_folder.png";
                tooltipKey = "status.repo.invalid_path";
                break;
            case PULL_PUSH_AVAILABLE:
                imgPath = "icon_pull_push.png";
                tooltipKey = "status.repo.pull_push_available";
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
                imgPath = "icon_missing_remote.png";
                tooltipKey = "status.repo.no_remote";
                break;
            case INACCESSIBLE_REMOTE:
                imgPath = "icon_lock.png";
                tooltipKey = "status.repo.no_auth_info";
                break;
            case WRONG_MASTER_PW:
                imgPath = "icon_attention.png";
                tooltipKey = "status.repo.wrong_master_password";
                break;
            case MERGE_NEEDED:
                imgPath = "icon_merge.png";
                tooltipKey = "status.repo.merge_required";
                break;
            case UP_TO_DATE:
                imgPath = "icon_check.png";
                tooltipKey = "status.repo.up_to_date";
                break;
        }
        if (imgPath != null) {
            iconAttention.setImage(ResourceStore.getImage(imgPath));
            lblIcon.setTooltip(new Tooltip(ResourceStore.getString(tooltipKey)));
        } else {
            iconAttention.setVisible(false);
            lblIcon.setTooltip(null);
        }
    }
}