package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for key press operations on repo lists
 * ENTER opens edit repo window.
 * DEL triggers deletion dialog.
 */
public class RepositoryInformationKeyPressHandler implements EventHandler<KeyEvent>, AlertDisplay {

    private ListView<RepositoryInformation> listView;
    private TableView<RepositoryInformation> tableView;

    public RepositoryInformationKeyPressHandler(ListView<RepositoryInformation> listView) {
        this.listView = listView;
    }

    public RepositoryInformationKeyPressHandler(TableView<RepositoryInformation> tableView) {
        this.tableView = tableView;
    }

    private List<RepositoryInformation> getSelectedItems() {
        if (listView != null) {
            return listView.getSelectionModel().getSelectedItems();
        } else {
            return tableView.getSelectionModel().getSelectedItems();
        }
    }

    private RepositoryInformation getSelectedItem() {
        if (listView != null) {
            return listView.getSelectionModel().getSelectedItem();
        } else {
            return tableView.getSelectionModel().getSelectedItem();
        }
    }

    @Override
    public void handle(KeyEvent event) {
        try {
            if (event.getCode() == KeyCode.ENTER) {
                RepositoryInformation repo = getSelectedItem();
                if (repo != null) {
                    ControllerEditRepo.openWindow(repo);
                }
            } else if (event.getCode() == KeyCode.DELETE) {
                List<RepositoryInformation> selItems = new ArrayList<>(getSelectedItems());
                if (!selItems.isEmpty()) {
                    if (showConfirmationDialog(Alert.AlertType.WARNING,
                            ResourceStore.getString("repo_list.confirm_delete_multiple.title"),
                            ResourceStore.getString("repo_list.confirm_delete_multiple.header", selItems.size()),
                            ResourceStore.getString("repo_list.confirm_delete_multiple.content"))) {

                        for (RepositoryInformation r : selItems) {
                            FileManager.getInstance().deleteRepo(r);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }
}