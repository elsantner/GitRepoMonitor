package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

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

    public static Map<UUID, AuthInfo> getFor(List<RepositoryInformation> repos, char[] masterPW) throws IOException {
        return secureStorage.getAuthInfos(masterPW, repos);
    }

    public static AuthInfo getFor(RepositoryInformation repo, char[] masterPW) throws IOException {
        switch (repo.getAuthMethod()) {
            case HTTPS:
                if (repo.isAuthenticated()) {
                    return new AuthInfo(secureStorage.getHttpsCredentialProvider(masterPW, repo.getID()));
                } else {
                    return new AuthInfo();
                }
            case SSL:
                // read ssl passphrase only if repo requires it (i.e. is authenticated), otherwise use custom ssl path if specified
                SSLTransportConfigCallback sslConfig;
                if (repo.isAuthenticated() && repo.getSslKeyPath() != null) {
                    SSLInformation sslInfo = secureStorage.getSslInformation(masterPW, repo.getID());
                    sslConfig = new SSLTransportConfigCallback(repo.getSslKeyPath(), sslInfo.getSslPassphrase());
                } else if (repo.getSslKeyPath() != null) {
                    sslConfig = new SSLTransportConfigCallback(repo.getSslKeyPath(), null);
                } else {
                    sslConfig = new SSLTransportConfigCallback();
                }
                return new AuthInfo(sslConfig);
            default:
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
