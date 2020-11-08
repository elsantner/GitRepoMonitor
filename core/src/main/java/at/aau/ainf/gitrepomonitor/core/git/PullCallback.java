package at.aau.ainf.gitrepomonitor.core.git;

public interface PullCallback {
    void finished(boolean success, Exception ex);
}
