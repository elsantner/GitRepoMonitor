package at.aau.ainf.gitrepomonitor.core.files;

/**
 * Listener for async file-related operations.
 */
public interface FileOperationCallback {
    void finished(boolean success, Exception ex);
}
