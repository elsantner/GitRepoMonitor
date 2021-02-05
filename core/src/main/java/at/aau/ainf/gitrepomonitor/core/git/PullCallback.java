package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.MergeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Callback for async pull command.
 * (For result of individual command.)
 */
public interface PullCallback {
    void finished(List<PullResult> results, int pullsSuccessful, int pullsFailed, boolean wrongMasterPW);

    default void finished(RepositoryInformation repo, MergeResult.MergeStatus status, Exception ex) {
        finished(Collections.singletonList(new PullResult(repo, status, ex)), 1, 0, false);
    }

    default void failed(RepositoryInformation repo, boolean wrongMasterPW) {
        finished(new ArrayList<>(), 0, 1, wrongMasterPW);
    }

    default void failed(RepositoryInformation repo,MergeResult.MergeStatus status, Exception ex, boolean wrongMasterPW) {
        finished(Collections.singletonList(new PullResult(repo, status, ex)), 0, 1, wrongMasterPW);
    }

    /**
     * Wrapper for pull result.
     */
    class PullResult {
        private final RepositoryInformation repo;
        private final MergeResult.MergeStatus status;
        private final Exception ex;

        public PullResult(RepositoryInformation repo, MergeResult.MergeStatus status, Exception ex) {
            this.repo = repo;
            this.status = status;
            this.ex = ex;
        }

        public RepositoryInformation getRepo() {
            return repo;
        }

        public MergeResult.MergeStatus getStatus() {
            return status;
        }

        public Exception getEx() {
            return ex;
        }
    }
}
