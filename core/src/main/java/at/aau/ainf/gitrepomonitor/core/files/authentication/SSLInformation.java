package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.UUID;

public class SSLInformation extends AuthenticationInformation {
    private String sslKeyPath;
    private String sslPassphrase;

    public SSLInformation(UUID repoID, String sslKeyPath, String sslPassphrase) {
        super(repoID);
        this.sslKeyPath = sslKeyPath;
        this.sslPassphrase = sslPassphrase;
    }

    public String getSslKeyPath() {
        return sslKeyPath;
    }

    public void setSslKeyPath(String sslKeyPath) {
        this.sslKeyPath = sslKeyPath;
    }

    public String getSslPassphrase() {
        return sslPassphrase;
    }

    public void setSslPassphrase(String sslPassphrase) {
        this.sslPassphrase = sslPassphrase;
    }
}
