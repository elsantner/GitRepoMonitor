package at.aau.ainf.gitrepomonitor.core.git;

/**
 * Callback for async status command.
 */
public interface UpdateStatusCallback {
    void finished(boolean success, int reposChecked, int reposFailedToCheck, Exception ex);
}
