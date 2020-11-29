package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;

public interface ConnectionTestCallback {
    void finished(RepositoryInformation.RepoStatus status);
}
