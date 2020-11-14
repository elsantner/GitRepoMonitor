package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;

import java.util.Collections;
import java.util.List;

public interface PullCallback {
    void finished(List<PullResult> results);

    default void finished(String repoPath, boolean success, Status status, Exception ex) {
        finished(Collections.singletonList(new PullResult(repoPath, success, status, ex)));
    }

    class PullResult {
        private String repoPath;
        private final boolean success;
        private final Status status;
        private final Exception ex;

        public PullResult(String repoPath, boolean success, Status status, Exception ex) {
            this.repoPath = repoPath;
            this.success = success;
            this.status = status;
            this.ex = ex;
        }

        public String getRepoPath() {
            return repoPath;
        }

        public boolean isSuccess() {
            return success;
        }

        public Status getStatus() {
            return status;
        }

        public Exception getEx() {
            return ex;
        }
    }

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
