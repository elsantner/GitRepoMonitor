package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

public class SSLTransportConfigCallback implements TransportConfigCallback {

    private String sslKeyPath;
    private byte[] sslKeyPassphrase;
    private JSch defaultJSch;

    public SSLTransportConfigCallback() {

    }

    public SSLTransportConfigCallback(String sslKeyPath, byte[] sslKeyPassphrase) {
        this.sslKeyPath = sslKeyPath;
        this.sslKeyPassphrase = sslKeyPassphrase;
    }

    @Override
    public void configure(Transport transport) {
        SshTransport sshTransport = ( SshTransport )transport;
        sshTransport.setSshSessionFactory( new JschConfigSessionFactory() {
            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                defaultJSch = super.createDefaultJSch(fs);
                if (sslKeyPath != null) {
                    defaultJSch.addIdentity(sslKeyPath, sslKeyPassphrase);
                }
                return defaultJSch;
            }
        });
    }

    /**
     * Clear the sslPassphrase stored in this object and the wrapped SSL identity.
     */
    public void clear() {
        Utils.clearArray(sslKeyPassphrase);
        sslKeyPassphrase = null;
        try {
            // removing a identity clears the ssl passphrase buffer.
            defaultJSch.removeAllIdentity();
        } catch (JSchException e) {
            // should not happen
        }
    }
}
