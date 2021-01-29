package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.naming.AuthenticationException;
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

    public static synchronized SecureFileStorage getInstance() {
        if (instance == null) {
            instance = new SecureFileStorage();
        }
        return instance;
    }

    private FileManager fileManager;

    protected SecureFileStorage() {
        super();
        this.fileManager = FileManager.getInstance();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    /**
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    @Override
    public boolean isMasterPasswordSet() {
        return fileManager.readAuthenticationString(MasterPasswordAuthInfo.ID) != null;
    }

    @Override
    public void setMasterPassword(char[] masterPW) throws AuthenticationException {
        if (isMasterPasswordSet()) {
            throw new AuthenticationException("master password was already set");
        }
        char[] hashedMP = Utils.sha3_256(masterPW);
        Utils.clearArray(masterPW);
        fileManager.storeAuthentication(new MasterPasswordAuthInfo(),
                encrypt(new String(hashedMP), hashedMP, MasterPasswordAuthInfo.ID.toString()));
        Utils.clearArray(hashedMP);
    }

    @Override
    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException {
        if (!isMasterPasswordSet()) {
            throw new AuthenticationException("master password was not set before");
        }
        char[] hashedCurrentPW = Utils.sha3_256(currentMasterPW);
        char[] hashedNewPW = Utils.sha3_256(newMasterPW);

        if (!isMasterPasswordCorrect(hashedCurrentPW)) {
            throw new AuthenticationException("wrong master password");
        }

        try {
            Map<UUID, String> encValues = fileManager.getAllAuthenticationStrings(true);
            for (UUID id : encValues.keySet()) {
                String newEncValue;
                // MP_SET requires separate handling as value must be the key
                if (id.equals(MasterPasswordAuthInfo.ID)) {
                    newEncValue = encrypt(new String(hashedNewPW), hashedNewPW, MasterPasswordAuthInfo.ID.toString());
                } else {
                    newEncValue = encrypt(decrypt(encValues.get(id), hashedCurrentPW, id.toString()),
                            hashedNewPW, id.toString());
                }
                fileManager.updateAuthentication(id, newEncValue);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new AuthenticationException("error during key change");
        }

        Utils.clearArray(currentMasterPW);
        Utils.clearArray(newMasterPW);
        Utils.clearArray(hashedCurrentPW);

        cacheMasterPasswordIfEnabled(hashedNewPW);
        // reset mp clear mechanisms
        resetMPUseCount();
        restartMPExpirationTimer();
    }

    /**
     * Convert authInfo to XML and encrypt using masterPW
     * @param authInfo
     * @param masterPW
     * @return
     */
    private String getEncryptedString(AuthenticationCredentials authInfo, char[] masterPW) {
        try {
            return encrypt(mapper.writeValueAsString(authInfo), masterPW, authInfo.getID().toString());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void store(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException {
        store(masterPW, Collections.singletonList(authInfo));
    }

    @Override
    public void store(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);

            if (!isMasterPasswordCorrect(masterPW)) {
                throw new AuthenticationException("wrong master password");
            }
            for (AuthenticationCredentials authInfo : authInfos) {
                String encString = getEncryptedString(authInfo, masterPW);
                fileManager.storeAuthentication(authInfo, encString);
                authInfo.destroy();
            }

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
        }
    }

    @Override
    public void store(AuthenticationCredentials authInfo) throws AuthenticationException {
        throwIfMasterPasswordNotCached();
        store(null, authInfo);
    }

    @Override
    public void update(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException {
        update(masterPW, Collections.singletonList(authInfo));
    }

    @Override
    public void update(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);

            if (!isMasterPasswordCorrect(masterPW)) {
                throw new AuthenticationException("wrong master password");
            }
            for (AuthenticationCredentials authInfo : authInfos) {
                String encString = getEncryptedString(authInfo, masterPW);
                fileManager.updateAuthentication(authInfo, encString);
                authInfo.destroy();
            }

            cacheMasterPasswordIfEnabled(masterPW);
            Utils.clearArray(masterPW);
            clearMasterPasswordIfRequired();
        }
    }

    @Override
    public void update(AuthenticationCredentials authInfo) throws AuthenticationException {
        throwIfMasterPasswordNotCached();
        update(null, authInfo);
    }

    @Override
    public void delete(UUID id) {
        fileManager.deleteAuthentication(id);
    }

    @Override
    public AuthenticationCredentials get(char[] masterPW, UUID id) throws AuthenticationException {
        return get(masterPW, Collections.singletonList(id)).get(id);
    }

    @Override
    public Map<UUID, AuthenticationCredentials> get(char[] masterPW, Collection<UUID> ids) throws AuthenticationException {
        synchronized (lockMasterPasswordReset) {
            masterPW = getCachedMasterPasswordHashIfPossible(masterPW);

            if (!isMasterPasswordCorrect(masterPW)) {
                throw new AuthenticationException("wrong master password");
            }

            Map<UUID, AuthenticationCredentials> authInfos = new HashMap<>();
            try {
                for (UUID id : ids) {
                    authInfos.put(id, mapper.readValue(decrypt(fileManager.readAuthenticationString(id), masterPW, id.toString()),
                            new TypeReference<>() {}));
                }
            } catch (BadPaddingException | IllegalBlockSizeException | JsonProcessingException e) {
                throw new AuthenticationException("authentication failed");
            } finally {
                cacheMasterPasswordIfEnabled(masterPW);
                Utils.clearArray(masterPW);
                clearMasterPasswordIfRequired();
            }
            return authInfos;
        }
    }

    @Override
    public AuthenticationCredentials get(UUID id) throws AuthenticationException {
        throwIfMasterPasswordNotCached();
        return get(null, id);
    }


    private boolean isMasterPasswordCorrect(char[] hashedCurrentPW) {
        String encHashedPW = fileManager.readAuthenticationString(MasterPasswordAuthInfo.ID);
        try {
            // check if decrypted password hash is equal to provided hash
            return encHashedPW != null && hashedCurrentPW != null &&
                    (decrypt(encHashedPW, hashedCurrentPW, MasterPasswordAuthInfo.ID.toString())
                            .equals(new String(hashedCurrentPW)));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void resetMasterPassword() {
        fileManager.clearAllAuthStrings();
        clearCachedMasterPassword();
    }
}
