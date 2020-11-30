package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
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
import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecureKeyringStorage extends SecureStorage {

    private static SecureKeyringStorage instance;
    private static final String SERVICE = "gitrepomonitor";
    private static final String MP_SET = "mpset";

    public static synchronized SecureKeyringStorage getInstance() {
        if (instance == null) {
            instance = new SecureKeyringStorage();
        }
        return instance;
    }

    private Keyring keyring;
    private boolean isSupported = true;

    protected SecureKeyringStorage() {
        super();
        try {
            keyring = Keyring.create();
        } catch (BackendNotSupportedException ex) {
            isSupported = false;
        }
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
            String pw = keyring.getPassword(SERVICE, MP_SET);
            return pw != null;
        } catch (PasswordAccessException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public void setMasterPassword(char[] masterPW) throws IOException {
        try {
            char[] hashedMP = sha3_256(masterPW);
            clearCharArray(masterPW);
            keyring.setPassword(SERVICE, MP_SET, encrypt(new String(hashedMP), hashedMP));
            clearCharArray(hashedMP);
        } catch (PasswordAccessException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException {
        // TODO: impl.
    }

    @Override
    public void storeHttpsCredentials(char[] masterPW, UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        try {
            masterPW = getAndCheckMasterPassword(masterPW);
            String xml = mapper.writeValueAsString(new HttpsCredentials(repoID, httpsUsername, httpsPassword));
            keyring.setPassword(SERVICE, repoID.toString(), encrypt(xml, masterPW));
            cacheMasterPasswordIfEnabled(masterPW);
        } catch (PasswordAccessException e) {
            throw new IOException("could not store credentials");
        } finally {
            clearCharArray(masterPW);
            clearCharArray(httpsPassword);
        }
    }

    @Override
    public void storeHttpsCredentials(UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        storeHttpsCredentials(null, repoID, httpsUsername, httpsPassword);
    }

    @Override
    public void deleteHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        try {
            masterPW = getAndCheckMasterPassword(masterPW);
            keyring.deletePassword(SERVICE, repoID.toString());
            cacheMasterPasswordIfEnabled(masterPW);
        } catch (PasswordAccessException ex) {
            throw new IOException(ex);
        } finally {
            clearCharArray(masterPW);
        }
    }

    @Override
    public void deleteHttpsCredentials(UUID repoID) throws IOException {
        deleteHttpsCredentials(null, repoID);
    }


    private HttpsCredentials getHttpsCredentialsHashedPW(char[] masterPW, UUID repoID) throws IOException {
        try {
            String credsCipher = keyring.getPassword(SERVICE, repoID.toString());
            String xml = decrypt(credsCipher, masterPW);
            HttpsCredentials creds = mapper.readValue(xml, new TypeReference<HttpsCredentials>(){});
            cacheMasterPasswordIfEnabled(masterPW);
            return creds;
        } catch (PasswordAccessException ex) {
            throw new IOException(ex);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new SecurityException("authentication failed");
        }
    }

    @Override
    public HttpsCredentials getHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        masterPW = getAndCheckMasterPassword(masterPW);
        return getHttpsCredentialsHashedPW(masterPW, repoID);
    }

    @Override
    public HttpsCredentials getHttpsCredentials(UUID repoID) throws IOException {
        return getHttpsCredentials(null, repoID);
    }

    @Override
    public CredentialsProvider getHttpsCredentialProvider(char[] masterPW, UUID repoID) throws IOException {
        masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
        HttpsCredentials credentials = getHttpsCredentialsHashedPW(masterPW, repoID);
        cacheMasterPasswordIfEnabled(masterPW);
        return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
    }

    @Override
    public CredentialsProvider getHttpsCredentialProvider(UUID repoID) throws IOException {
        return getHttpsCredentialProvider(null, repoID);
    }

    @Override
    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(char[] masterPW, List<RepositoryInformation> repos) throws IOException {
        Map<UUID, CredentialsProvider> map = new HashMap<>();

        for (RepositoryInformation repo : repos) {
            try {
                if (repo.isAuthenticated()) {
                    CredentialsProvider credProv = getHttpsCredentialProvider(masterPW, repo.getID());
                    map.put(repo.getID(), credProv);
                }
            } catch (IOException ex) {
                // means that repo has no stored credentialscontinue loop
            }
        }
        return map;
    }

    @Override
    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(List<RepositoryInformation> repos) throws IOException {
        return getHttpsCredentialProviders(null, repos);
    }

    private boolean isMasterPasswordCorrect(char[] masterPW) throws PasswordAccessException {
        String pw = keyring.getPassword(SERVICE, MP_SET);
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
