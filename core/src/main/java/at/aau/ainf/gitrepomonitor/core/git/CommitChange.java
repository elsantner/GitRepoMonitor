package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.List;

/**
 * Wrapper for a commit change.
 * Stores commit and all associated file changes.
 */
public class CommitChange {
    private RevCommit commit;
    private List<DiffEntry> fileChanges;

    public CommitChange(RevCommit commit, List<DiffEntry> fileChanges) {
        this.commit = commit;
        this.fileChanges = fileChanges;
    }

    public RevCommit getCommit() {
        return commit;
    }

    public List<DiffEntry> getFileChanges() {
        return fileChanges;
    }
}
