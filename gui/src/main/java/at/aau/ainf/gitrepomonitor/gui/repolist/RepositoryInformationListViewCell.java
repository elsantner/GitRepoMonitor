package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom list cell for repositories
 */
public class RepositoryInformationListViewCell extends ListCell<RepositoryInformation> {
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
     * Create list cell.
     * @param isDraggable If true, then cell item can be dragged and dropped to switch position.
     */
    public RepositoryInformationListViewCell(boolean isDraggable) {
        this.fileManager = FileManager.getInstance();
        if (isDraggable) {
            setupDragAndDrop();
        }
    }

    /**
     * Allow the user to change the order of repos by using drag & drop
     */
    private void setupDragAndDrop() {
        ListCell<RepositoryInformation> thisCell = this;

        // store dragged list item
        setOnDragDetected(event -> {
            if (getItem() == null) {
                return;
            }

            Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(getListView().getItems().indexOf(getItem())));
            dragboard.setContent(content);

            event.consume();
        });

        // show move action is allowed
        setOnDragOver(event -> {
            if (event.getGestureSource() != thisCell &&
                    event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });

        // grey out repo hovered over
        setOnDragEntered(event -> {
            if (event.getGestureSource() != thisCell &&
                    event.getDragboard().hasString()) {
                setOpacity(0.3);
            }
        });

        // restore full color if drag exited
        setOnDragExited(event -> {
            if (event.getGestureSource() != thisCell &&
                    event.getDragboard().hasString()) {
                setOpacity(1);
            }
        });

        // handle position swap
        setOnDragDropped(event -> {
            if (getItem() == null) {
                return;
            }

            Dragboard db = event.getDragboard();
            boolean success = false;

            // if string (i.e. dragged item index) is present
            if (db.hasString()) {
                ObservableList<RepositoryInformation> items = getListView().getItems();

                int draggedIdx = Integer.parseInt(db.getString());
                int thisIdx = items.indexOf(getItem());

                // swap position of items
                RepositoryInformation draggedItem = items.get(draggedIdx);
                RepositoryInformation droppedItem = items.get(thisIdx);
                items.set(draggedIdx, droppedItem);
                items.set(thisIdx, draggedItem);

                List<RepositoryInformation> itemscopy = new ArrayList<>(getListView().getItems());
                getListView().getItems().setAll(itemscopy);

                success = true;
                // update custom index
                draggedItem.setCustomOrderIndex(thisIdx);
                droppedItem.setCustomOrderIndex(draggedIdx);
                fileManager.editRepo(draggedItem.getPath(), draggedItem);
                fileManager.editRepo(droppedItem.getPath(), droppedItem);
            }
            event.setDropCompleted(success);
            event.consume();
        });

        setOnDragDone(DragEvent::consume);
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
