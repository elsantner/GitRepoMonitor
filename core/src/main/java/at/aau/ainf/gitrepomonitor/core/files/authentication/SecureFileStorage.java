package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.naming.AuthenticationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;


/**
 * The idea of this credential storage system is as follows:
 * - The user can store repo credentials by inputting a master password
 * - The repository carries information whether it has associated credentials or not (in plaintext)
 * - All repo credentials are stored in a single file
 * - Upon requesting credentials, the whole file is loaded and decrypted
 * - Only the required credentials stay loaded, the rest is discarded
 */
public class SecureFileStorage extends SecureStorage {

    private static SecureFileStorage instance;
    private static final String CREDENTIALS_FILENAME = "creds";

    public static synchronized SecureFileStorage getInstance() {
        if (instance == null) {
            instance = new SecureFileStorage();
        }
        return instance;
    }

    protected SecureFileStorage() {
        super();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    protected String getCredentialsFilename() {
        return CREDENTIALS_FILENAME;
    }

    /**
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    @Override
    public boolean isMasterPasswordSet() {
        File credentialsFile = new File(Utils.getProgramHomeDir() + getCredentialsFilename());
        return (credentialsFile.exists() && !credentialsFile.isDirectory());
    }

    @Override
    public void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException {
        if (isMasterPasswordSet()) {
            throw new AuthenticationException("master password was already set");
        }
        char[] hashedPW = Utils.sha3_256(masterPW);
        writeCredentials(new CredentialWrapper(), hashedPW);
        Utils.clearArray(masterPW);
        Utils.clearArray(hashedPW);
    }

    @Override
    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException {
        if (!isMasterPasswordSet()) {
            throw new AuthenticationException("master password was not set before");
        }
        char[] hashedCurrentPW = Utils.sha3_256(currentMasterPW);
        char[] hashedNewPW = Utils.sha3_256(newMasterPW);
        CredentialWrapper wrapper = readCredentials(hashedCurrentPW);
        writeCredentials(wrapper, hashedNewPW);
        Utils.clearArray(currentMasterPW);
        Utils.clearArray(newMasterPW);
        Utils.clearArray(hashedCurrentPW);
        cacheMasterPasswordIfEnabled(hashedNewPW);
        // reset mp clear mechanisms
        resetMPUseCount();
        restartMPExpirationTimer();
    }

    @Override
    public void storeHttpsCredentials(char[] masterPW, UUID repoID,
                                         String httpsUsername, char[] httpsPassword) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            CredentialWrapper allCredentials = readCredentials(masterPW);
            HttpsCredentials newCredentials = new HttpsCredentials(repoID, httpsUsername, httpsPassword);
            allCredentials.putCredentials(newCredentials);
            writeCredentials(allCredentials, masterPW);

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            Utils.clearArray(httpsPassword);
            clearMasterPasswordIfRequired();
        }
    }

    public void storeHttpsCredentials(UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        throwIfMasterPasswordNotCached();
        storeHttpsCredentials(null, repoID, httpsUsername, httpsPassword);
    }

    public void deleteHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            CredentialWrapper allCredentials = readCredentials(masterPW);
            allCredentials.removeCredentials(repoID);
            writeCredentials(allCredentials, masterPW);

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
        }
    }

    public void deleteHttpsCredentials(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        deleteHttpsCredentials(null, repoID);
    }

    public HttpsCredentials getHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            HttpsCredentials credentials = getHttpsCredentialsHashed(masterPW, repoID);
            clearMasterPasswordIfRequired();
            return credentials;
        }
    }

    private HttpsCredentials getHttpsCredentialsHashed(char[] masterPWHash, UUID repoID) throws IOException {
        CredentialWrapper allCredentials = readCredentials(masterPWHash);
        HttpsCredentials credentials = allCredentials.getCredentials(repoID);
        cacheMasterPasswordIfEnabled(masterPWHash);
        Utils.clearArray(masterPWHash);
        return credentials;
    }

    private SSLInformation getSslInformationHashed(char[] masterPWHash, UUID repoID) throws IOException {
        CredentialWrapper allCredentials = readCredentials(masterPWHash);
        SSLInformation sslInformation = allCredentials.getSslInformation(repoID);
        cacheMasterPasswordIfEnabled(masterPWHash);
        Utils.clearArray(masterPWHash);
        return sslInformation;
    }

    public HttpsCredentials getHttpsCredentials(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentials(null, repoID);
    }

    public UsernamePasswordCredentialsProvider getHttpsCredentialProvider(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            char[] masterPwCopy = Arrays.copyOf(masterPW, masterPW.length);
            HttpsCredentials credentials = getHttpsCredentialsHashed(masterPW, repoID);
            UsernamePasswordCredentialsProvider cp = new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
            cacheMasterPasswordIfEnabled(masterPwCopy);
            Utils.clearArray(masterPwCopy);
            clearMasterPasswordIfRequired();
            return cp;
        }
    }

    public UsernamePasswordCredentialsProvider getHttpsCredentialProvider(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentialProvider(null, repoID);
    }

    public Map<UUID, UsernamePasswordCredentialsProvider> getHttpsCredentialProviders(char[] masterPW, List<RepositoryInformation> repos) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            Map<UUID, UsernamePasswordCredentialsProvider> map = new HashMap<>();
            CredentialWrapper allCredentials = readCredentials(masterPW);
            for (HttpsCredentials credentials : allCredentials.getHttpsCredentials()) {
                map.put(credentials.getRepoID(),
                        new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword()));
            }
            for (RepositoryInformation repo : repos) {
                if (repo.getAuthMethod() != RepositoryInformation.AuthMethod.HTTPS) {
                    map.remove(repo.getID());
                }
            }
            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
            return map;
        }
    }

    public Map<UUID, UsernamePasswordCredentialsProvider> getHttpsCredentialProviders(List<RepositoryInformation> repos) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentialProviders(null, repos);
    }

    @Override
    public void storeSslInformation(char[] masterPW, UUID repoID, byte[] sslPassphrase) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            CredentialWrapper allCredentials = readCredentials(masterPW);
            SSLInformation newSslInfo = new SSLInformation(repoID, sslPassphrase);
            allCredentials.putSslInformation(newSslInfo);
            writeCredentials(allCredentials, masterPW);

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            Utils.clearArray(sslPassphrase);
            clearMasterPasswordIfRequired();
        }
    }

    @Override
    public void storeSslInformation(UUID repoID, byte[] sslPassphrase) throws IOException {
        throwIfMasterPasswordNotCached();
        storeSslInformation(null, repoID, sslPassphrase);
    }

    @Override
    public void deleteSslInformation(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            CredentialWrapper allCredentials = readCredentials(masterPW);
            allCredentials.removeSslInformation(repoID);
            writeCredentials(allCredentials, masterPW);

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
        }
    }

    @Override
    public void deleteSslInformation(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        deleteSslInformation(null, repoID);
    }

    @Override
    public SSLInformation getSslInformation(char[] masterPW, UUID repoID) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            SSLInformation sslInformation = getSslInformationHashed(masterPW, repoID);
            clearMasterPasswordIfRequired();
            return sslInformation;
        }
    }

    @Override
    public SSLInformation getSslInformation(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getSslInformation(null ,repoID);
    }

    @Override
    public Map<UUID, AuthInfo> getAuthInfos(char[] masterPW, List<RepositoryInformation> repos) throws IOException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);
            Map<UUID, AuthInfo> map = new HashMap<>();
            CredentialWrapper allCredentials = readCredentials(masterPW);

            for (RepositoryInformation repo : repos) {
                if (repo.getAuthMethod() == RepositoryInformation.AuthMethod.HTTPS) {
                    HttpsCredentials credentials = allCredentials.getCredentials(repo.getID());
                    map.put(repo.getID(), new AuthInfo(new UsernamePasswordCredentialsProvider(
                            credentials.getUsername(), credentials.getPassword())));
                } else if (repo.getAuthMethod() == RepositoryInformation.AuthMethod.SSL) {
                    if (repo.getSslKeyPath() != null && !repo.getSslKeyPath().isBlank()) {
                        SSLInformation sslInfo = allCredentials.getSslInformation(repo.getID());
                        map.put(repo.getID(), new AuthInfo(new SSLTransportConfigCallback(
                                repo.getSslKeyPath(), sslInfo != null ? sslInfo.getSslPassphrase() : null)));
                    }
                }
            }

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
            return map;
        }
    }

    @Override
    public void resetMasterPassword() throws IOException {
        openCredentialsFile().delete();
        FileManager.getInstance().clearAllAuthRequirements();
        clearCachedMasterPassword();
    }

    @Override
    public boolean isIntact(List<RepositoryInformation> authRequiredRepos) {
        if (authRequiredRepos.isEmpty()) {
            return true;
        } else {
            return isMasterPasswordSet();
        }
    }

    protected CredentialWrapper readCredentials(char[] masterPW) throws IOException {
        File credsFile = openCredentialsFile();
        byte[] bytes;
        String credentialsXml;

        try (FileInputStream fis = new FileInputStream(credsFile)) {
            bytes = fis.readAllBytes();
            credentialsXml = decryptFromBytes(bytes, masterPW);
            return mapper.readValue(credentialsXml, new TypeReference<CredentialWrapper>(){});
        } catch (BadPaddingException | IllegalBlockSizeException | JsonParseException e) {
            throw new SecurityException("authentication failed");
        }
    }

    protected void writeCredentials(CredentialWrapper credentials, char[] masterPW) throws IOException {
        File credsFile = openCredentialsFile();
        byte[] bytes;
        String credentialsXml;

        try (FileOutputStream fos = new FileOutputStream(credsFile)) {
            credentialsXml = mapper.writeValueAsString(credentials);
            bytes = encryptToBytes(credentialsXml, masterPW);
            fos.write(bytes);
            fos.flush();
        }
    }

    private File openCredentialsFile() throws IOException {
        File credentialsFile = new File(Utils.getProgramHomeDir() + getCredentialsFilename());
        if (credentialsFile.exists() && credentialsFile.isDirectory()) {
            throw new IOException("could not locate credentials file");
        } else if (!credentialsFile.exists()) {
            if (!credentialsFile.createNewFile()) {
                throw new IOException("could create credentials file");
            }
        }
        return credentialsFile;
    }
}
