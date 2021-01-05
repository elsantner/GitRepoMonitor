package at.aau.ainf.gitrepomonitor.core.git;

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
                JSch defaultJSch = super.createDefaultJSch(fs);
                if (sslKeyPath != null) {
                    defaultJSch.addIdentity(sslKeyPath, sslKeyPassphrase);
                }
                return defaultJSch;
            }
        });
    }
}
