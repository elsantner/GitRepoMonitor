package at.aau.ainf.gitrepomonitor.core.git;

public interface PullCallback {
    void finished(boolean success, boolean wasUpdated, Exception ex);
}
