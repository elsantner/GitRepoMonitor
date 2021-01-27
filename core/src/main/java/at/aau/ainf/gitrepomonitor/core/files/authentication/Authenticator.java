package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.naming.AuthenticationException;
import java.util.*;

public class Authenticator {
    private static final SecureStorage secureStorage = SecureStorage.getImplementation();

    private UsernamePasswordCredentialsProvider cp;
    private SSLTransportConfigCallback ssl;

    public Authenticator(UsernamePasswordCredentialsProvider cp) {
        this.cp = cp;
    }

    public Authenticator(SSLTransportConfigCallback ssl) {
        this.ssl = ssl;
    }

    public Authenticator() {
    }

    public static Map<UUID, Authenticator> getFor(List<RepositoryInformation> repos, char[] masterPW) throws AuthenticationException {
        Map<UUID, Authenticator> authInfos = new HashMap<>();
        Set<UUID> authIDs = new HashSet<>();
        for (RepositoryInformation r : repos) {
            if (r.getAuthID() != null) {
                authIDs.add(r.getAuthID());
            }
        }
        Map<UUID, AuthenticationInformation> authInfoMap = secureStorage.get(masterPW, authIDs);
        for (RepositoryInformation r : repos) {
            authInfos.put(r.getID(), convertToAuthInfo(authInfoMap.get(r.getAuthID())));
        }

        return authInfos;
    }

    public static Authenticator getFor(RepositoryInformation repo, char[] masterPW) throws AuthenticationException {
        if (repo.getAuthID() != null) {
            return getFor(Collections.singletonList(repo), masterPW).get(repo.getID());
        } else {
            return new Authenticator();
        }
    }

    private static Authenticator convertToAuthInfo(AuthenticationInformation ai) {
        if (ai instanceof HttpsCredentials) {
            return new Authenticator(new UsernamePasswordCredentialsProvider(
                    ((HttpsCredentials) ai).getUsername(), ((HttpsCredentials) ai).getPassword()));
        } else if (ai instanceof SSLInformation) {
            return new Authenticator(new SSLTransportConfigCallback(
                    ((SSLInformation) ai).getSslKeyPath(), ((SSLInformation) ai).getSslPassphrase()));
        } else {
            return new Authenticator();
        }
    }

    public static Authenticator get(UUID authId, char[] masterPW) throws AuthenticationException {
        if (authId != null) {
            AuthenticationInformation ai = secureStorage.get(masterPW, authId);
            if (ai instanceof HttpsCredentials) {
                return new Authenticator(new UsernamePasswordCredentialsProvider(
                        ((HttpsCredentials) ai).getUsername(), ((HttpsCredentials) ai).getPassword()));
            } else if (ai instanceof SSLInformation) {
                return new Authenticator(new SSLTransportConfigCallback(
                        ((SSLInformation) ai).getSslKeyPath(), ((SSLInformation) ai).getSslPassphrase()));
            } else {
                return new Authenticator();
            }
        } else {
            return new Authenticator();
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
