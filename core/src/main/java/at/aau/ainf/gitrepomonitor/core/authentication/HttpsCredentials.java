package at.aau.ainf.gitrepomonitor.core.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;

/**
 * Wrapper for HTTPS credentials (Username & password)
 */
public class HttpsCredentials extends AuthenticationCredentials {
    private String username;
    private char[] password;

    public HttpsCredentials() {
        super();
    }

    public HttpsCredentials(String username, char[] password) {
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
    public RepositoryInformation.AuthMethod getAuthMethod() {
        return RepositoryInformation.AuthMethod.HTTPS;
    }

    @Override
    public void destroy() {
        Utils.clearArray(password);
    }
}
