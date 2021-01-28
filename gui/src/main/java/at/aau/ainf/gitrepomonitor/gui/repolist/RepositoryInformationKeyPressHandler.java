package at.aau.ainf.gitrepomonitor.gui.repolist;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import at.aau.ainf.gitrepomonitor.gui.AlertDisplay;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import at.aau.ainf.gitrepomonitor.gui.auth.ControllerEditAuth;
import at.aau.ainf.gitrepomonitor.gui.editrepo.ControllerEditRepo;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepositoryInformationKeyPressHandler implements EventHandler<KeyEvent>, AlertDisplay {

    private ListView<RepositoryInformation> listView;

    public RepositoryInformationKeyPressHandler(ListView<RepositoryInformation> listView) {
        this.listView = listView;
    }

    @Override
    public void handle(KeyEvent event) {
        try {
            if (event.getCode() == KeyCode.ENTER) {
                RepositoryInformation repo = listView.getSelectionModel().getSelectedItem();
                if (repo != null) {
                    ControllerEditRepo.openWindow(repo);
                }
            } else if (event.getCode() == KeyCode.DELETE) {
                List<RepositoryInformation> selItems = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
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