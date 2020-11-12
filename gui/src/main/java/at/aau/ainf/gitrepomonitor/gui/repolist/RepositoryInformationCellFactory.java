package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

public class RepositoryInformationCellFactory
        implements Callback<ListView<RepositoryInformation>, ListCell<RepositoryInformation>> {

    private StatusDisplay statusDisplay;
    private GitManager gitManager;

    public RepositoryInformationCellFactory() {
        this.gitManager = GitManager.getInstance();
    }

    public RepositoryInformationCellFactory(StatusDisplay statusDisplay) {
        this();
        this.statusDisplay = statusDisplay;
    }

    @Override
    public ListCell<RepositoryInformation> call(ListView<RepositoryInformation> listView) {
        ListCell<RepositoryInformation> cell = new RepositoryInformationListViewCell();

        // ctx menu only on non-empty list entries
        cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(getContextMenu(cell));
            }
        });
        cell.prefWidthProperty().bind(listView.prefWidthProperty());
        return cell;
    }

    /**
     * Returns a setup & configured ContextMenu for the given cell
     * @param cell The cell for which the context menu is created
     * @return setup ContextMenu
     */
    private ContextMenu getContextMenu(ListCell<RepositoryInformation> cell) {
        return new RepositoryInformationContextMenu(cell, statusDisplay);
    }
}
