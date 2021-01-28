package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.MergeResult;

public interface PullListener {
    void pullExecuted(RepositoryInformation repo, MergeResult.MergeStatus status);
}
