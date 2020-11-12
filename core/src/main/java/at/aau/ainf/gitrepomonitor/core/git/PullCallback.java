package at.aau.ainf.gitrepomonitor.core.git;

public interface PullCallback {
    void finished(boolean success, Status status, Exception ex);

    enum Status {
        FAST_FORWARD,
        FAST_FORWARD_SQUASHED,
        ALREADY_UP_TO_DATE,
        FAILED,
        MERGED,
        MERGED_SQUASHED,
        MERGED_SQUASHED_NOT_COMMITTED,
        CONFLICTING,
        ABORTED,
        MERGED_NOT_COMMITTED,
        NOT_SUPPORTED,
        CHECKOUT_CONFLICT
    }
}
