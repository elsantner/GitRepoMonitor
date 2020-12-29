package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.UUID;

public abstract class AuthenticationInformation {
    protected UUID repoID;

    protected AuthenticationInformation() {
        // for serialization
    }

    protected AuthenticationInformation(UUID repoID) {
        this.repoID = repoID;
    }

    public UUID getRepoID() {
        return repoID;
    }
    public void setRepoID(UUID repoID) {
        this.repoID = repoID;
    }
}
