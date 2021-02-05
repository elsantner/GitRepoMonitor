package at.aau.ainf.gitrepomonitor.core.git;

import java.util.List;

/**
 * Callback for async log command.
 */
public interface LogCallback {

    void finished(boolean success, List<CommitChange> changes, Exception ex);

    default void finished(boolean success, List<CommitChange> changes) {
        finished(success, changes, null);
    }

    default void finished(Exception ex) {
        finished(false, null, ex);
    }
}
