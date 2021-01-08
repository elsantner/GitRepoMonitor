package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Utils;

import java.util.UUID;

public class HttpsCredentials extends AuthenticationInformation {
    private String username;
    private char[] password;

    public HttpsCredentials() {
        super();
    }

    public HttpsCredentials(UUID repoID, String username, char[] password) {
        super(repoID);
        this.username = username;
        this.password = password;
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

    @Override
    public void destroy() {
        Utils.clearArray(password);
    }
}
