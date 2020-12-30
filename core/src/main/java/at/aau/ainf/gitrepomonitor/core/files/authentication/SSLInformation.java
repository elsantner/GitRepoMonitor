package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.UUID;

public class SSLInformation extends AuthenticationInformation {
    private String sslPassphrase;

    public SSLInformation() {
        // for serialization
    }

    public SSLInformation(UUID repoID, String sslPassphrase) {
        super(repoID);
        this.sslPassphrase = sslPassphrase;
    }

    public String getSslPassphrase() {
        return sslPassphrase;
    }

    public void setSslPassphrase(String sslPassphrase) {
        this.sslPassphrase = sslPassphrase;
    }
}
