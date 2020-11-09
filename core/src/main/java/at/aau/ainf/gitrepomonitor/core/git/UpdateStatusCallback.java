package at.aau.ainf.gitrepomonitor.core.git;

public interface UpdateStatusCallback {
    void finished(boolean success, Exception ex);
}
