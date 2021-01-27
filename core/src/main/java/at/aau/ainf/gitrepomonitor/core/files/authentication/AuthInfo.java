package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthInfo {
    private static final SecureStorage secureStorage = SecureStorage.getImplementation();

    private UsernamePasswordCredentialsProvider cp;
    private SSLTransportConfigCallback ssl;

    public AuthInfo(UsernamePasswordCredentialsProvider cp) {
        this.cp = cp;
    }

    public AuthInfo(SSLTransportConfigCallback ssl) {
        this.ssl = ssl;
    }

    public AuthInfo() {
    }

    public static Map<UUID, AuthInfo> getFor(List<RepositoryInformation> repos, char[] masterPW) throws AuthenticationException {
        Map<UUID, AuthInfo> authInfos = new HashMap<>();
        for (RepositoryInformation r : repos) {
            authInfos.put(r.getID(), getFor(r, masterPW));
        }
        return authInfos;
    }

    public static AuthInfo getFor(RepositoryInformation repo, char[] masterPW) throws AuthenticationException {
        if (repo.getAuthID() != null) {
            AuthenticationInformation ai = secureStorage.get(masterPW, repo.getAuthID());
            if (ai instanceof HttpsCredentials) {
                return new AuthInfo(new UsernamePasswordCredentialsProvider(
                        ((HttpsCredentials) ai).getUsername(), ((HttpsCredentials) ai).getPassword()));
            } else if (ai instanceof SSLInformation) {
                return new AuthInfo(new SSLTransportConfigCallback(
                        ((SSLInformation) ai).getSslKeyPath(), ((SSLInformation) ai).getSslPassphrase()));
            } else {
                return new AuthInfo();
            }
        } else {
            return new AuthInfo();
        }
    }

    public static AuthInfo get(UUID authId, char[] masterPW) throws AuthenticationException {
        if (authId != null) {
            AuthenticationInformation ai = secureStorage.get(masterPW, authId);
            if (ai instanceof HttpsCredentials) {
                return new AuthInfo(new UsernamePasswordCredentialsProvider(
                        ((HttpsCredentials) ai).getUsername(), ((HttpsCredentials) ai).getPassword()));
            } else if (ai instanceof SSLInformation) {
                return new AuthInfo(new SSLTransportConfigCallback(
                        ((SSLInformation) ai).getSslKeyPath(), ((SSLInformation) ai).getSslPassphrase()));
            } else {
                return new AuthInfo();
            }
        } else {
            return new AuthInfo();
        }
    }

    public <C extends GitCommand<T>, T> void configure(TransportCommand<C, T> cmd) {
        if (cp != null) {
            cmd.setCredentialsProvider(cp);
        } else if (ssl != null) {
            cmd.setTransportConfigCallback(ssl);
        }
    }

    public boolean hasInformation() {
        return cp != null || ssl != null;
    }

    /**
     * Clear all stored credential information
     */
    public void destroy() {
        if (cp != null) {
            cp.clear();
            cp = null;
        }
        if (ssl != null) {
            ssl.clear();
            ssl = null;
        }
    }
}
