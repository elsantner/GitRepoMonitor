package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.api.MergeResult;

import java.util.Collections;
import java.util.List;

public interface PullCallback {
    void finished(List<PullResult> results);

    default void finished(String repoPath, MergeResult.MergeStatus status, Exception ex) {
        finished(Collections.singletonList(new PullResult(repoPath, status, ex)));
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
