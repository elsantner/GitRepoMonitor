package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.Utils;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.diff.DiffEntry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Implements functionality to configure JSch SSL connection used by JGit.
 */
public class SSLTransportConfigCallback implements TransportConfigCallback {

    private final String sslKeyPath;
    private byte[] sslKeyPassphrase;

    public SSLTransportConfigCallback(String sslKeyPath, byte[] sslKeyPassphrase) {
        this.sslKeyPath = sslKeyPath;
        this.sslKeyPassphrase = sslKeyPassphrase;
    }

    @Override
    public void configure(Transport transport) {
        SshTransport sshTransport = (SshTransport)transport;
        // Setup custom credential provider for private key passphrase
        String encodedPassphrase = new String(sslKeyPassphrase, StandardCharsets.UTF_8);
        sshTransport.setCredentialsProvider(new CustomCredentialProvider(encodedPassphrase));
        // Setup custom ssh factory for private key path
        SshSessionFactory sshFactory = new CustomSshSessionFactory(sslKeyPath);
        sshTransport.setSshSessionFactory(sshFactory);
    }

    /**
     * Clear the sslPassphrase stored in this object and the wrapped SSL identity.
     */
    public void clear() {
        Utils.clearArray(sslKeyPassphrase);
        sslKeyPassphrase = null;
    }
}
