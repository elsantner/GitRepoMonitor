package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.util.Callback;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.IOException;

/**
 * Custom table row factory for repositories
 */
public class RepoTableRowFactory
        implements Callback<TableView<RepositoryInformation>, TableRow<RepositoryInformation>> {

    private StatusDisplay statusDisplay;
    private ProgressMonitor progressMonitor;

    public RepoTableRowFactory() {
        super();
    }

    /**
     * Create cell factory for repository information
     * @param statusDisplay displayStatus() is called when a status update occurs due to context menu operation
     * @param progressMonitor used for Git operations
     */
    public RepoTableRowFactory(StatusDisplay statusDisplay, ProgressMonitor progressMonitor) {
        this();
        this.statusDisplay = statusDisplay;
        this.progressMonitor = progressMonitor;
    }

    private void openEditRepoWindow(RepositoryInformation repo) {
        try {
            ControllerEditRepo.openWindow(repo);
        } catch (IOException e) {
            statusDisplay.displayStatus("Error opening Edit window");
        }
    }

    /**
     * Returns a setup & configured ContextMenu for the given cell
     * @param row The row (repo) for which the context menu is created
     * @return setup ContextMenu
     */
    private ContextMenu getContextMenu(TableRow<RepositoryInformation> row) {
        return new RepoContextMenu(row, statusDisplay, progressMonitor);
    }

    @Override
    public TableRow<RepositoryInformation> call(TableView<RepositoryInformation> tblView) {
        TableRow<RepositoryInformation> row = new TableRow<>();

        // ctx menu only on non-empty list entries
        row.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                row.setContextMenu(null);
            } else {
                row.setContextMenu(getContextMenu(row));
                // open edit repo window on double click or enter-press
                row.setOnMouseClicked(mouseClickedEvent -> {
                    if (mouseClickedEvent.getButton().equals(MouseButton.PRIMARY) &&
                            mouseClickedEvent.getClickCount() == 2) {
                        openEditRepoWindow(row.getItem());
                    }
                });
            }
        });
        row.prefWidthProperty().bind(tblView.prefWidthProperty());
        return row;
    }
}
