package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Utils;

import java.util.UUID;

public class SSLInformation extends AuthenticationInformation {
    private byte[] sslPassphrase;

    public SSLInformation() {
        // for serialization
    }

    public SSLInformation(UUID repoID, byte[] sslPassphrase) {
        super(repoID);
        this.sslPassphrase = sslPassphrase;
    }

    public byte[] getSslPassphrase() {
        return sslPassphrase;
    }

    public void setSslPassphrase(byte[] sslPassphrase) {
        this.sslPassphrase = sslPassphrase;
    }

    @Override
    public void destroy() {
        Utils.clearArray(sslPassphrase);
    }
}
