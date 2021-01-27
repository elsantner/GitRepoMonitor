package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;

import java.util.UUID;

public class SSLInformation extends AuthenticationInformation {
    private String sslKeyPath;
    private byte[] sslPassphrase;

    public SSLInformation() {
        // for serialization
    }

    public SSLInformation(String sslKeyPath, byte[] sslPassphrase) {
        this.sslKeyPath = sslKeyPath;
        this.sslPassphrase = sslPassphrase;
    }

    public byte[] getSslPassphrase() {
        return sslPassphrase;
    }

    public void setSslPassphrase(byte[] sslPassphrase) {
        this.sslPassphrase = sslPassphrase;
    }

    public String getSslKeyPath() {
        return sslKeyPath;
    }

    public void setSslKeyPath(String sslKeyPath) {
        this.sslKeyPath = sslKeyPath;
    }

    @Override
    public RepositoryInformation.AuthMethod getAuthMethod() {
        return RepositoryInformation.AuthMethod.SSL;
    }

    @Override
    public void destroy() {
        Utils.clearArray(sslPassphrase);
    }
}
