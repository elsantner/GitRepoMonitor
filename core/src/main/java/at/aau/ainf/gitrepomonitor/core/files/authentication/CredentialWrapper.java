package at.aau.ainf.gitrepomonitor.core.files.authentication;

import java.util.*;

public class CredentialWrapper {
    private Map<UUID, HttpsCredentials> httpsCredentials;

    public CredentialWrapper() {
        this.httpsCredentials = new HashMap<>();
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
}
