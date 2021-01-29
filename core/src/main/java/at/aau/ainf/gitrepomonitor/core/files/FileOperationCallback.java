package at.aau.ainf.gitrepomonitor.core.files;

public interface FileOperationCallback {
    void finished(boolean success, Exception ex);
}
