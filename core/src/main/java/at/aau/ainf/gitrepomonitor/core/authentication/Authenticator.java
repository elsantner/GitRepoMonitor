package at.aau.ainf.gitrepomonitor.core.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.naming.AuthenticationException;
import java.util.*;

/**
 * Wrapper for implementation specific authentication objects.
 * Can be used to configure JGit commands with the stored authentication credentials.
 */
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

    /**
     * Get all available Authenticators for all provided RepositoryInformation objects.
     * The credentials are loaded using {@code masterPW}. If no MP is provided, the cached one is used.
     * If the MP is not cached and not provided, a {@link AuthenticationException} is thrown.
     * @param repos Repositories to get authenticators for.
     * @param masterPW Master Password
     * @return Map of UUID of repo --> corresponding authenticator (or null if {@code repo.authID} == null)
     * @throws AuthenticationException If authentication fails
     */
    public static Map<UUID, Authenticator> getFor(List<RepositoryInformation> repos, char[] masterPW) throws AuthenticationException {
        Map<UUID, Authenticator> authenticators = new HashMap<>();
        Set<UUID> authIDs = new HashSet<>();
        // get all required AuthIDs (repos can share AuthIDs)
        for (RepositoryInformation r : repos) {
            if (r.getAuthID() != null) {
                authIDs.add(r.getAuthID());
            }
        }
        // load authentication credentials
        Map<UUID, AuthenticationCredentials> authInfoMap = secureStorage.get(masterPW, authIDs);
        for (RepositoryInformation r : repos) {
            authenticators.put(r.getID(), convertToAuthenticator(authInfoMap.get(r.getAuthID())));
        }
        return authenticators;
    }

    /**
     * Get Authenticator for provided repo.
     * @param repo Repo to get authenticator for.
     * @param masterPW Master Password. If this is null, the cached one is used.
     * @return Authenticator for {@code repo}, or null if {@code repo.authID} == null.
     * @throws AuthenticationException If authentication fails
     */
    public static Authenticator getFor(RepositoryInformation repo, char[] masterPW) throws AuthenticationException {
        if (repo.getAuthID() != null) {
            return getFor(Collections.singletonList(repo), masterPW).get(repo.getID());
        } else {
            return new Authenticator();
        }
    }

    /**
     * Get Authenticator for provided AuthenticationCredentials
     * @param ac Authentication credentials
     * @return Authenticator wrapping provided auth credentials
     */
    private static Authenticator convertToAuthenticator(AuthenticationCredentials ac) {
        if (ac instanceof HttpsCredentials) {
            return new Authenticator(new UsernamePasswordCredentialsProvider(
                    ((HttpsCredentials) ac).getUsername(), ((HttpsCredentials) ac).getPassword()));
        } else if (ac instanceof SslCredentials) {
            return new Authenticator(new SSLTransportConfigCallback(
                    ((SslCredentials) ac).getSslKeyPath(), ((SslCredentials) ac).getSslPassphrase()));
        } else {
            return new Authenticator();
        }
    }

    /**
     * Get Authenticator for auth credentials with provided authID.
     * @param authId ID of auth credentials to load.
     * @param masterPW Master Password. If this is null, the cached one is used.
     * @return Authenticator with given ID. (or a blank Authenticator if {@code authId} == null)
     * @throws AuthenticationException If authentication fails
     */
    public static Authenticator get(UUID authId, char[] masterPW) throws AuthenticationException {
        if (authId != null) {
            AuthenticationCredentials ai = secureStorage.get(masterPW, authId);
            if (ai instanceof HttpsCredentials) {
                return new Authenticator(new UsernamePasswordCredentialsProvider(
                        ((HttpsCredentials) ai).getUsername(), ((HttpsCredentials) ai).getPassword()));
            } else if (ai instanceof SslCredentials) {
                return new Authenticator(new SSLTransportConfigCallback(
                        ((SslCredentials) ai).getSslKeyPath(), ((SslCredentials) ai).getSslPassphrase()));
            } else {
                return new Authenticator();
            }
        } else {
            return new Authenticator();
        }
    }

    /**
     * Add authentication credentials to command.
     * @param cmd Command to add auth to.
     */
    public <C extends GitCommand<T>, T> void configure(TransportCommand<C, T> cmd) {
        if (cp != null) {
            cmd.setCredentialsProvider(cp);
        } else if (ssl != null) {
            cmd.setTransportConfigCallback(ssl);
        }
    }

    /**
     * Check if Authenticator has stored auth credentials.
     * @return True, if either HTTPS or SSL credentials are stored.
     */
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
