package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.git.Branch;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;

/**
 * Custom list cell to display branches.
 */
public class BranchListCell extends ListCell<Branch> {

    @Override
    public void updateItem(Branch item, boolean empty) {
        super.updateItem(item, empty);
        if (item != null) {
            setText(item.getShortName());
            if (item.isRemoteOnly()) {
                ImageView icon = new ImageView(ResourceStore.getImage("icon_remote.png"));
                icon.setPreserveRatio(true);
                icon.setFitHeight(25);
                setGraphic(icon);
            } else {
                setGraphic(null);
            }
        }
        else {
            setText(null);
            setGraphic(null);
        }
    }
}
