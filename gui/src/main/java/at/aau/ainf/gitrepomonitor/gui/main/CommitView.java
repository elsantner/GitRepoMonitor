package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.git.CommitChange;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CommitView extends Region {
    public static final int MIN_HEIGHT = 35;
    public static int FILE_DISPLAYED_DEFAULT = 0;

    private static final Image iconAdded;
    private static final Image iconEdited;
    private static final Image iconRemoved;

    static {
        iconAdded = new Image("/at/aau/ainf/gitrepomonitor/gui/icons/icon_added.png");
        iconEdited = new Image("/at/aau/ainf/gitrepomonitor/gui/icons/icon_edited.png");
        iconRemoved = new Image("/at/aau/ainf/gitrepomonitor/gui/icons/icon_removed.png");
    }

    @FXML
    private Label lblMsg;
    @FXML
    private Label lblDate;
    @FXML
    private Label lblUsername;
    @FXML
    private VBox containerMain;
    @FXML
    private VBox boxFileChanges;
    @FXML
    private Tooltip ttCommitMessage;
    @FXML
    private Tooltip ttUsername;

    private Hyperlink linkShowAll;

    private FXMLLoader loader;
    private static DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public CommitView(CommitChange commitChange) {
        if (loader == null) {
            loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/commit_view.fxml"));
            loader.setController(this);

            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // fill display elements with data
        lblMsg.setText(commitChange.getCommit().getShortMessage());
        ttCommitMessage.setText(commitChange.getCommit().getFullMessage());
        lblDate.setText(df.format(new Date((long)commitChange.getCommit().getCommitTime() * 1000)));
        lblUsername.setText(commitChange.getCommit().getAuthorIdent().getName());
        ttUsername.setText(getFullAuthorName(commitChange.getCommit().getAuthorIdent()));
        addFileChanges(commitChange.getFileChanges());

        this.getChildren().add(containerMain);
    }

    private String getFullAuthorName(PersonIdent author) {
        return author.getName() +
                " <" + author.getEmailAddress() + ">";
    }

    private void addFileChanges(List<DiffEntry> fileChanges) {
        for (int i=0; i<Math.min(FILE_DISPLAYED_DEFAULT, fileChanges.size()); i++) {
            boxFileChanges.getChildren().add(new FileChange(fileChanges.get(i)));
        }
        // if there are more file changes to display, add a link to show them all
        if (FILE_DISPLAYED_DEFAULT < fileChanges.size()) {
            Hyperlink linkShowAll = new Hyperlink(ResourceStore.getString("commitlog.show_all_changes", fileChanges.size()));
            // if link is clicked remove it and add remaining file changes
            linkShowAll.setOnAction(event -> {
                boxFileChanges.getChildren().remove(linkShowAll);
                for (int i=FILE_DISPLAYED_DEFAULT; i<fileChanges.size(); i++) {
                    boxFileChanges.getChildren().add(new FileChange(fileChanges.get(i)));
                }
            });
            boxFileChanges.getChildren().add(linkShowAll);
        }
    }

    private class FileChange extends Region {

        @FXML
        private ImageView iconChange;
        @FXML
        private Label lblFileName;
        @FXML
        private HBox container;

        private FXMLLoader loader;

        private FileChange(DiffEntry fileChange) {
            if (loader == null) {
                loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/file_change.fxml"));
                loader.setController(this);

                try {
                    loader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (fileChange.getChangeType() == DiffEntry.ChangeType.DELETE) {
                lblFileName.setText(fileChange.getOldPath());
            } else {
                lblFileName.setText(fileChange.getNewPath());
            }
            switch (fileChange.getChangeType()) {
                case ADD:
                    iconChange.setImage(iconAdded);
                    break;
                case MODIFY:
                    iconChange.setImage(iconEdited);
                    break;
                case DELETE:
                    iconChange.setImage(iconRemoved);
                    break;
            }

            this.getChildren().add(container);
        }
    }
}
