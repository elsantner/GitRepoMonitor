package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;

/**
 * Callback for async connection test.
 */
public interface ConnectionTestCallback {
    void finished(RepositoryInformation.RepoStatus status);
}
