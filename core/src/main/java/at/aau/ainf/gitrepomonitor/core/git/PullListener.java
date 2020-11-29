package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.api.MergeResult;

public interface PullListener {
    void pullExecuted(String path, MergeResult.MergeStatus status);
}
