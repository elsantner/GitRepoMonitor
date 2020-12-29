package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.api.MergeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface PullCallback {
    void finished(List<PullResult> results, int pullsSuccessful, int pullsFailed, boolean wrongMasterPW);

    default void finished(String repoPath, MergeResult.MergeStatus status, Exception ex) {
        finished(Collections.singletonList(new PullResult(repoPath, status, ex)), 1, 0, false);
    }

    default void failed(String repoPath, boolean wrongMasterPW) {
        finished(new ArrayList<>(), 0, 1, wrongMasterPW);
    }

    default void failed(String repoPath, MergeResult.MergeStatus status, Exception ex, boolean wrongMasterPW) {
        finished(Collections.singletonList(new PullResult(repoPath, status, ex)), 0, 1, wrongMasterPW);
    }

    class PullResult {
        private String repoPath;
        private final MergeResult.MergeStatus status;
        private final Exception ex;

        public PullResult(String repoPath, MergeResult.MergeStatus status, Exception ex) {
            this.repoPath = repoPath;
            this.status = status;
            this.ex = ex;
        }

        public String getRepoPath() {
            return repoPath;
        }

        public MergeResult.MergeStatus getStatus() {
            return status;
        }

        public Exception getEx() {
            return ex;
        }
    }
}
