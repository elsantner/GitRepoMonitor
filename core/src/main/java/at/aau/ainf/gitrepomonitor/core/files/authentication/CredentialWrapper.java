package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.*;

public class CredentialWrapper {
    private Map<UUID, HttpsCredentials> httpsCredentials;
    private Map<UUID, SSLInformation> sslInformation;

    public CredentialWrapper() {
        this.httpsCredentials = new HashMap<>();
        this.sslInformation = new HashMap<>();
    }

    public Set<HttpsCredentials> getHttpsCredentials() {
        if (httpsCredentials == null) {
            httpsCredentials = new HashMap<>();
        }
        return new HashSet<>(httpsCredentials.values());
    }

    public synchronized void setHttpsCredentials(Set<HttpsCredentials> credentials) {
        if (credentials != null) {
            this.httpsCredentials.clear();
            credentials.forEach(cred -> this.httpsCredentials.put(cred.getRepoID(), cred));
        }
    }

    public HttpsCredentials getCredentials(UUID repoID) {
        return httpsCredentials.get(repoID);
    }

    public void putCredentials(HttpsCredentials newCredentials) {
        httpsCredentials.put(newCredentials.getRepoID(), newCredentials);
    }

    public void removeCredentials(UUID repoID) {
        httpsCredentials.remove(repoID);
    }

    public Set<SSLInformation> getSslInformation() {
        if (sslInformation == null) {
            sslInformation = new HashMap<>();
        }
        return new HashSet<>(sslInformation.values());
    }

    public synchronized void setSslInformation(Set<SSLInformation> sslInformation) {
        if (sslInformation != null) {
            this.sslInformation.clear();
            sslInformation.forEach(cred -> this.sslInformation.put(cred.getRepoID(), cred));
        }
    }

    public SSLInformation getSslInformation(UUID repoID) {
        return sslInformation.get(repoID);
    }

    public void putSslInformation(SSLInformation sslInformation) {
        this.sslInformation.put(sslInformation.getRepoID(), sslInformation);
    }

    public void removeSslInformation(UUID repoID) {
        sslInformation.remove(repoID);
    }
}
