package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.StatusDisplay;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.beans.property.ObjectProperty;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.IOException;

public class RepositoryInformationCellFactory
        implements Callback<ListView<RepositoryInformation>, ListCell<RepositoryInformation>> {

    private StatusDisplay statusDisplay;
    private ProgressMonitor progressMonitor;
    private Control keyListener;
    private boolean isDraggable;

    public RepositoryInformationCellFactory() {
        super();
    }

    /**
     * Create cell factory for repository information
     * @param statusDisplay displayStatus() is called when a status update occurs due to context menu operation
     * @param progressMonitor used for Git operations
     * @param keyListener provides key-events for list cells
     */
    public RepositoryInformationCellFactory(StatusDisplay statusDisplay, ProgressMonitor progressMonitor,
                                            Control keyListener, boolean isDraggable) {
        this();
        this.statusDisplay = statusDisplay;
        this.progressMonitor = progressMonitor;
        this.keyListener = keyListener;
        this.isDraggable = isDraggable;
    }

    @Override
    public ListCell<RepositoryInformation> call(ListView<RepositoryInformation> listView) {
        ListCell<RepositoryInformation> cell = new RepositoryInformationListViewCell(isDraggable);

        // ctx menu only on non-empty list entries
        cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
            if (isNowEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(getContextMenu(cell));
                // open edit repo window on double click or enter-press
                cell.setOnMouseClicked(mouseClickedEvent -> {
                    if (mouseClickedEvent.getButton().equals(MouseButton.PRIMARY) &&
                            mouseClickedEvent.getClickCount() == 2) {
                        openEditRepoWindow(cell.getItem());
                    }
                });
                keyListener.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        openEditRepoWindow(cell.getItem());
                    }
                });
            }
        });
        cell.prefWidthProperty().bind(listView.prefWidthProperty());
        return cell;
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
     * @param cell The cell for which the context menu is created
     * @return setup ContextMenu
     */
    private ContextMenu getContextMenu(ListCell<RepositoryInformation> cell) {
        return new RepositoryInformationContextMenu(cell, statusDisplay, progressMonitor);
    }
}
