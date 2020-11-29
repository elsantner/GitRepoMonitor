package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.UUID;

public class HttpsCredentials {
    private UUID repoID;
    private String username;
    private char[] password;

    public HttpsCredentials() {
        // for serialization
    }

    public HttpsCredentials(UUID repoID, String username, char[] password) {
        this.repoID = repoID;
        this.username = username;
        this.password = password;
    }

    public UUID getRepoID() {
        return repoID;
    }

    public void setRepoID(UUID repoID) {
        this.repoID = repoID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setPassword(char[] password) {
        this.password = password;
    }
}
