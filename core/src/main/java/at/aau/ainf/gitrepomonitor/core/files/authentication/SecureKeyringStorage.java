package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.KeyringStorageType;
import com.github.javakeyring.PasswordAccessException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecureKeyringStorage extends SecureStorage {

    private static SecureKeyringStorage instance;
    protected static final String SERVICE = "gitrepomonitor";
    protected static final String MP_SET = "mpset";

    public static synchronized SecureKeyringStorage getInstance() {
        if (instance == null) {
            instance = new SecureKeyringStorage();
        }
        return instance;
    }

    protected Keyring keyring;
    private boolean isSupported = true;

    protected SecureKeyringStorage() {
        super();
        try {
            keyring = Keyring.create();
        } catch (BackendNotSupportedException ex) {
            isSupported = false;
        }
    }

    protected String getServiceName() {
        return SERVICE;
    }

    public KeyringStorageType getStorageType() {
        return keyring.getKeyringStorageType();
    }

    @Override
    public boolean isSupported() {
        return isSupported;
    }

    @Override
    public boolean isMasterPasswordSet() {
        try {
            String pw = keyring.getPassword(getServiceName(), MP_SET);
            return pw != null;
        } catch (PasswordAccessException ex) {
            return false;
        }
    }

    @Override
    public void setMasterPassword(char[] masterPW) throws IOException {
        try {
            char[] hashedMP = sha3_256(masterPW);
            clearCharArray(masterPW);
            keyring.setPassword(getServiceName(), MP_SET, encrypt(new String(hashedMP), hashedMP));
            clearCharArray(hashedMP);
        } catch (PasswordAccessException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException {
        updateMasterPassword(currentMasterPW, newMasterPW, FileManager.getInstance().getAllAuthenticatedRepos());
    }

    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW, List<RepositoryInformation> affectedRepos) throws AuthenticationException, IOException {
        if (!isMasterPasswordSet()) {
            throw new AuthenticationException("master password was not set before");
        }
        char[] hashedCurrentPW = sha3_256(currentMasterPW);
        char[] hashedNewPW = sha3_256(newMasterPW);

        try {
            if (!isMasterPasswordCorrect(hashedCurrentPW)) {
                throw new AuthenticationException("wrong master password");
            }

            for (RepositoryInformation repo : affectedRepos) {
                if (repo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
                    HttpsCredentials creds = readHttpsCredentials(hashedCurrentPW, repo.getID());
                    writeHttpsCredentials(hashedNewPW, repo.getID(), creds.getUsername(), creds.getPassword());
                }
            }
            keyring.setPassword(getServiceName(), MP_SET, encrypt(new String(hashedNewPW), hashedNewPW));
            cacheMasterPasswordIfEnabled(hashedNewPW);
            // reset mp clear mechanisms
            resetMPUseCount();
            restartMPExpirationTimer();
        } catch (PasswordAccessException e) {
            throw new AuthenticationException("authentication failed");
        }
    }

    @Override
    public void storeHttpsCredentials(char[] masterPW, UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        synchronized (lockMasterPasswordReset) {
            try {
                masterPW = getAndCheckMasterPassword(masterPW);
                writeHttpsCredentials(masterPW, repoID, httpsUsername, httpsPassword);
                cacheMasterPasswordIfEnabled(masterPW);
                clearMasterPasswordIfRequired();
            } catch (PasswordAccessException e) {
                throw new IOException("could not store credentials");
            } finally {
                clearCharArray(masterPW);
                clearCharArray(httpsPassword);
            }
        }
    }

    private void writeHttpsCredentials(char[] masterPW, UUID repoID, String httpsUsername, char[] httpsPassword) throws JsonProcessingException, PasswordAccessException {
        String xml = mapper.writeValueAsString(new HttpsCredentials(repoID, httpsUsername, httpsPassword));
        keyring.setPassword(getServiceName(), repoID.toString(), encrypt(xml, masterPW));
    }

    @Override
    public void storeHttpsCredentials(UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        storeHttpsCredentials(null, repoID, httpsUsername, httpsPassword);
    }

    @Override
    public void deleteHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            try {
                masterPW = getAndCheckMasterPassword(masterPW);
                keyring.deletePassword(getServiceName(), repoID.toString());
                cacheMasterPasswordIfEnabled(masterPW);
                clearMasterPasswordIfRequired();
            } catch (PasswordAccessException ex) {
                throw new IOException(ex);
            } finally {
                clearCharArray(masterPW);
            }
        }
    }

    @Override
    public void deleteHttpsCredentials(UUID repoID) throws IOException {
        deleteHttpsCredentials(null, repoID);
    }


    private HttpsCredentials readHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        try {
            String credsCipher = keyring.getPassword(getServiceName(), repoID.toString());
            String xml = decrypt(credsCipher, masterPW);
            return mapper.readValue(xml, new TypeReference<HttpsCredentials>(){});
        } catch (PasswordAccessException ex) {
            throw new IOException(ex);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new SecurityException("authentication failed");
        }
    }

    @Override
    public HttpsCredentials getHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getAndCheckMasterPassword(masterPW);
            HttpsCredentials creds = readHttpsCredentials(masterPW, repoID);
            cacheMasterPasswordIfEnabled(masterPW);
            clearMasterPasswordIfRequired();
            return creds;
        }
    }

    @Override
    public HttpsCredentials getHttpsCredentials(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentials(null, repoID);
    }

    @Override
    public CredentialsProvider getHttpsCredentialProvider(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            HttpsCredentials credentials = readHttpsCredentials(masterPW, repoID);
            cacheMasterPasswordIfEnabled(masterPW);
            clearMasterPasswordIfRequired();
            return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
        }
    }

    @Override
    public CredentialsProvider getHttpsCredentialProvider(UUID repoID) throws IOException {
        return getHttpsCredentialProvider(null, repoID);
    }

    @Override
    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(char[] masterPW, List<RepositoryInformation> repos) {
        synchronized (lockMasterPasswordReset) {
            Map<UUID, CredentialsProvider> map = new HashMap<>();

            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            for (RepositoryInformation repo : repos) {
                try {
                    if (repo.isAuthenticated()) {
                        HttpsCredentials credentials = readHttpsCredentials(masterPW, repo.getID());
                        CredentialsProvider credProv = new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
                        map.put(repo.getID(), credProv);
                    }
                } catch (IOException ex) {
                    // means that repo has no stored credentials, continue loop
                }
            }
            cacheMasterPasswordIfEnabled(masterPW);
            clearMasterPasswordIfRequired();
            return map;
        }
    }

    @Override
    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(List<RepositoryInformation> repos) {
        return getHttpsCredentialProviders(null, repos);
    }

    /**
     * Check if for all given repos a valid password entry exists.
     * If not, all other entries are deleted.
     * @param authRequiredRepos Repos which require stored credentials information.
     * @return True, if credentials are intact
     */
    @Override
    public boolean isIntact(List<RepositoryInformation> authRequiredRepos) {
        if (authRequiredRepos.isEmpty()) {
            return true;
        } else {
            if (!isMasterPasswordSet()) {
                return false;
            } else {
                try {
                    for (RepositoryInformation repo : authRequiredRepos) {
                        keyring.getPassword(SERVICE, repo.getID().toString());
                    }
                    return true;
                } catch (PasswordAccessException ex) {
                    for (RepositoryInformation repo : authRequiredRepos) {
                        try {
                            keyring.deletePassword(SERVICE, repo.getID().toString());
                        } catch (PasswordAccessException e) {
                            // means that password did not exist so no need for any action
                        }
                    }
                    return false;
                }
            }
        }
    }

    private boolean isMasterPasswordCorrect(char[] masterPW) throws PasswordAccessException {
        String pw = keyring.getPassword(getServiceName(), MP_SET);
        try {
            return (decrypt(pw, masterPW).equals(new String(masterPW)));
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            return false;
        }
    }

    private char[] getAndCheckMasterPassword(char[] masterPW) throws SecurityException {
        return getAndCheckMasterPassword(masterPW, false);
    }

    private char[] getAndCheckMasterPassword(char[] masterPW, boolean isHashed) throws SecurityException {
        if (isMasterPasswordCached()) {
            return getCachedMasterPasswordHashIfPossible(masterPW);
        } else {
            if (!isHashed) {
                char[] hashedPW = sha3_256(masterPW);
                clearCharArray(masterPW);
                masterPW = hashedPW;
            }
            throwIfMasterPasswordIncorrect(masterPW);
            return masterPW;
        }
    }

    private void throwIfMasterPasswordIncorrect(char[] masterPW) throws SecurityException {
        try {
            if (masterPW == null || !isMasterPasswordCorrect(masterPW)) {
                throw new SecurityException("authentication failed");
            }
        } catch (PasswordAccessException e) {
            throw new SecurityException("authentication failed");
        }
    }
}
