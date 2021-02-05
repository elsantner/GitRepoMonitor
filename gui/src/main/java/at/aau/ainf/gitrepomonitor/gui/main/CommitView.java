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
import javafx.scene.paint.Color;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Custom GUI element to display commits including file changes.
 */
public class CommitView extends Region {
    public static final int MIN_HEIGHT = 35;
    public static int FILE_DISPLAYED_DEFAULT = 0;

    private static final Image iconAdded;
    private static final Image iconEdited;
    private static final Image iconRemoved;

    static {
        iconAdded = ResourceStore.getImage("icon_added.png");
        iconEdited = ResourceStore.getImage("icon_edited.png");
        iconRemoved = ResourceStore.getImage("icon_removed.png");
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
    @FXML
    private Hyperlink linkShowAll;
    @FXML
    private Label lblNewChange;

    private FXMLLoader loader;
    private static DateFormat df = new SimpleDateFormat(ResourceStore.getString("date_time_format"));

    public CommitView(CommitChange commitChange) {
        // load FXML and setup GUI
        if (loader == null) {
            loader = new FXMLLoader(
                    getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/commit_view.fxml"),
                    ResourceStore.getResourceBundle());
            loader.setController(this);

            try {
                loader.load();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // fill display elements with data
        lblMsg.setText(commitChange.getCommit().getShortMessage());
        ttCommitMessage.setText(commitChange.getCommit().getId().getName() + "\n" +
                commitChange.getCommit().getFullMessage());
        lblDate.setText(df.format(new Date((long)commitChange.getCommit().getCommitTime() * 1000)));
        lblUsername.setText(commitChange.getCommit().getAuthorIdent().getName());
        lblUsername.setTextFill(getUserColor(commitChange.getCommit().getAuthorIdent()));
        ttUsername.setText(getFullAuthorName(commitChange.getCommit().getAuthorIdent()));
        lblNewChange.managedProperty().bind(lblNewChange.visibleProperty());

        addFileChanges(commitChange.getFileChanges());

        this.getChildren().add(containerMain);
    }

    /**
     * Generate a deterministic color for the person.
     * (Currently just uses email)
     * @param author Person to get color for
     * @return Deterministic color
     */
    private Color getUserColor(PersonIdent author) {
        int hash = author.getEmailAddress().hashCode();
        int r = hash & 0x0000FF;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0xFF0000) >> 16;

        return Color.rgb(r, g, b);
    }

    /**
     * Get author and email
     * @param author Author
     * @return Author name and email
     */
    private String getFullAuthorName(PersonIdent author) {
        return author.getName() +
                " <" + author.getEmailAddress() + ">";
    }

    /**
     * Add all file changes behind a link.
     * If user clicks on link, the changes are listed.
     * @param fileChanges File changes.
     */
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

    public void setNew(boolean isNew) {
        lblNewChange.setVisible(isNew);
    }

    /**
     * Custom GUI element to display file changes.
     */
    private static class FileChange extends Region {

        @FXML
        private ImageView iconChange;
        @FXML
        private Label lblFileName;
        @FXML
        private HBox container;

        private FXMLLoader loader;

        private FileChange(DiffEntry fileChange) {
            // load FXML and setup GUI
            if (loader == null) {
                loader = new FXMLLoader(
                        getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/file_change.fxml"),
                        ResourceStore.getResourceBundle());
                loader.setController(this);

                try {
                    loader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // set file path
            if (fileChange.getChangeType() == DiffEntry.ChangeType.DELETE) {
                lblFileName.setText(fileChange.getOldPath());
            } else {
                lblFileName.setText(fileChange.getNewPath());
            }
            // set change type icon
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
