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
                return new AuthInfo(secureStorage.getHttpsCredentialProvider(masterPW, repo.getID()));
            case SSL:
                // TODO: add custom ssl path support
                SSLTransportConfigCallback sslConfig;
                if (repo.getSslKeyPath() != null) {
                    sslConfig = new SSLTransportConfigCallback(repo.getSslKeyPath(),
                            secureStorage.getSslInformation(masterPW, repo.getID()).getSslPassphrase());
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

    public void destroy() {
        cp.clear();
        cp = null;
        ssl = null;
    }
}
