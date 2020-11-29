package at.aau.ainf.gitrepomonitor.core.git;

public interface UpdateStatusCallback {
    void finished(boolean success, int reposChecked, int reposFailedToCheck, Exception ex);
}
