package at.aau.ainf.gitrepomonitor.git;

public interface PullCallback {
    void finished(boolean success, Exception ex);
}
