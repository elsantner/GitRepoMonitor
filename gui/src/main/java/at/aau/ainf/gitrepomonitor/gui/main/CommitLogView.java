package at.aau.ainf.gitrepomonitor.gui.main;

import at.aau.ainf.gitrepomonitor.core.git.CommitChange;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Displays commits including changed files.
 */
public class CommitLogView extends ScrollPane {

    @FXML
    private VBox containerCommitLog;

    private final Object lock = true;
    private List<CommitChange> currentCommitLog = new ArrayList<>();
    private int currentCommitLogDisplayIndex;      // index up to which changes are currently displayed
    private ScheduledThreadPoolExecutor timer;
    private boolean isTimerTaskScheduled = false;

    public CommitLogView() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/at/aau/ainf/gitrepomonitor/gui/main/commit_log_view.fxml"));
        loader.setController(this);
        loader.setRoot(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.timer = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
        setup();
    }

    public List<CommitChange> getCommitLog() {
        return currentCommitLog;
    }

    public void setCommitLog(List<CommitChange> currentCommitLog) {
        synchronized (lock) {       // avoid any problems with quickly switching between selected repos
            if (currentCommitLog == null) {
                throw new NullPointerException("commit log must not be null");
            }

            this.currentCommitLog = currentCommitLog;
            displayCommitChanges();
        }
    }

    private void setup() {
        this.vvalueProperty().addListener((observable, oldValue, newValue) -> {
            // load 10 more commits if scrolled in the bottom 25% of the list
            if ((double)newValue > this.getVmax() * 0.75) {
                loadMoreCommitChanges(currentCommitLogDisplayIndex, 10);
            }
        });

        // load appropriate amount of commits if the display size is changed
        this.heightProperty().addListener((observable, oldValue, newValue) -> {
            isTimerTaskScheduled = true;
            timer.schedule(() -> Platform.runLater(() -> {
                displayCommitChanges();
                isTimerTaskScheduled = false;
            }), 1000, TimeUnit.MILLISECONDS);
        });
    }

    /**
     * Displays commit changes (residing in "currentCommitLog") in the specified range
     * @param startIndex Start index
     * @param maxCount Maximum count of displayed changes (will be less if not enough currentCommitLogs are present)
     * @return Displayed commit changes.
     */
    private int loadMoreCommitChanges(int startIndex, int maxCount) {
        synchronized (lock) {       // avoid any problems with quickly switching between selected repos
            int i = 0;
            System.out.println(startIndex + " " + maxCount);
            for (; i < maxCount && startIndex + i < currentCommitLog.size(); i++) {
                addCommitView(currentCommitLog.get(startIndex + i));
            }
            currentCommitLogDisplayIndex += i;
            return i;
        }
    }

    private void addCommitView(CommitChange commitChange) {
        CommitView commitView = new CommitView(commitChange);
        containerCommitLog.getChildren().add(commitView);
    }

    /**
     * Clears the commit log display and displays first entries (residing in "currentCommitLog")
     * to fill up the visible portion of the log.
     * Use loadMoreCommitChanges() to load more entries.
     */
    private void displayCommitChanges() {
        containerCommitLog.getChildren().clear();
        currentCommitLogDisplayIndex = 0;
        int numVisibleEntries = (int)(this.getHeight() / CommitView.MIN_HEIGHT) + 1;
        loadMoreCommitChanges(0, Math.min(currentCommitLog.size(), numVisibleEntries));
    }
}
