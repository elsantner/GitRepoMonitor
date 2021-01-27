package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.logging.Logger;


public abstract class SecureStorage {

    // salt for AES ciphers
    protected static final String SALT = "3JN3DXVqcVxzxtZK";
    protected static SecureStorageSettings settings;
    protected static XmlMapper mapper;
    protected static File fileSettings = new File(Utils.getProgramHomeDir() + "settings.xml");

    protected char[] masterPassword;
    protected int mpUseCount = 0;
    protected Timer timer;
    protected TimerTask mpExpirationTimerTask;
    protected final Object lockMasterPasswordReset = new Object();

    static {
        mapper = XmlMapper.xmlBuilder().build();
        loadSettings();
    }

    /**
     * Gets the instance of the preferred storage type set in the settings.
     * If a storage type is not supported, the default file storage is returned.
     * @return Preferred (and supported) secure storage instance.
     */
    public static SecureStorage getImplementation() {
        if (settings.isUseKeyring()) {
            if (SecureKeyringStorage.getInstance().isSupported()) {
                return SecureKeyringStorage.getInstance();
            } else {
                settings.setUseKeyring(false);
                persistSettings();
                return SecureFileStorage.getInstance();
            }
        } else {
            return SecureFileStorage.getInstance();
        }
    }

    private static void loadSettings() {
        try {
            settings = mapper.readValue(fileSettings, new TypeReference<>() {});
        } catch (IOException e) {
            e.printStackTrace();
            settings = new SecureStorageSettings();
        }
    }

    private static void persistSettings() {
        try {
            mapper.writeValue(fileSettings, settings);
            Logger.getAnonymousLogger().info("Wrote settings to " + fileSettings.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    protected SecureStorage() {
        // defeat instantiation
    }

    /**
     * Returns whether the SecureStorage implementation is supported in the user's environment or not.
     * @return True, if supported.
     */
    public abstract boolean isSupported();

    /**
     * Set whether to cache the master password.
     * If enabled, once any method has been called using a valid master password,
     * it is stored internally and subsequent functions requiring the master password
     * can be called without it.
     * @param cacheMasterPassword True, if master password should be cached.
     */
    public synchronized void enableMasterPasswordCache(boolean cacheMasterPassword) {
        settings.setCacheEnabled(cacheMasterPassword);
        if (!cacheMasterPassword) {
            masterPassword = null;
            stopMPExpirationTimer();
        }
        persistSettings();
    }

    public boolean isMasterPasswordCacheEnabled() {
        return settings.isCacheEnabled();
    }

    public synchronized void setMasterPasswordCacheMethod(SecureStorageSettings.CacheClearMethod method, Integer value) {
        settings.setClearMethod(method);
        settings.setClearValue(value);
        persistSettings();
        // reset mp cache & clearing mechanisms
        resetMPUseCount();
        stopMPExpirationTimer();
        clearCachedMasterPassword();
    }

    public SecureStorageSettings getSettings() {
        return (SecureStorageSettings) settings.clone();
    }

    /**
     * @return True, if the master password is currently cached.
     */
    public boolean isMasterPasswordCached() {
        return masterPassword != null;
    }

    public void clearCachedMasterPassword() {
        if (masterPassword != null) {
            Utils.clearArray(masterPassword);
            masterPassword = null;
        }
    }

    protected synchronized void cacheMasterPasswordIfEnabled(char[] masterPassword) {
        if (settings.isCacheEnabled()) {
            this.masterPassword = Arrays.copyOf(masterPassword, masterPassword.length);
        }
    }

    protected void throwIfMasterPasswordNotCached() {
        if (!isMasterPasswordCached()) {
            throw new SecurityException("master password not cached but required");
        }
    }

    protected char[] getCachedMasterPasswordHashIfPossible(char[] mp) {
        if (mp == null || isMasterPasswordCached()) {
            throwIfMasterPasswordNotCached();
            return Arrays.copyOf(masterPassword, masterPassword.length);
        }
        char[] hashedPW = Utils.sha3_256(mp);
        Utils.clearArray(mp);
        return hashedPW;
    }

    /**
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    public abstract boolean isMasterPasswordSet();

    public abstract void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException;

    public abstract void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException;

    public abstract void store(char[] masterPW, AuthenticationInformation authInfo) throws AuthenticationException;

    public abstract void store(char[] masterPW, Collection<AuthenticationInformation> authInfos) throws AuthenticationException;

    public abstract void store(AuthenticationInformation authInfo) throws AuthenticationException;

    public abstract void update(char[] masterPW, AuthenticationInformation authInfo) throws AuthenticationException;

    public abstract void update(char[] masterPW, Collection<AuthenticationInformation> authInfos) throws AuthenticationException;

    public abstract void update(AuthenticationInformation authInfo) throws AuthenticationException;

    public abstract void delete(UUID id);

    public abstract AuthenticationInformation get(char[] masterPW, UUID id) throws AuthenticationException;

    public abstract Map<UUID, AuthenticationInformation> get(char[] masterPW, Collection<UUID> ids) throws AuthenticationException;

    public abstract AuthenticationInformation get(UUID id) throws AuthenticationException;

    public abstract void resetMasterPassword() throws IOException;

    protected byte[] encryptToBytes(String plaintext, char[] key) {
        try {
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    protected String decryptFromBytes(byte[] ciphertext, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(ciphertext));
    }

    protected String encrypt(String plaintext, char[] key) {
        try {
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    protected String decrypt(String ciphertext, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)));
    }

    private Cipher getCipherInstantiation(int cipherMode, char[] key) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(key, SALT.getBytes(), 65536, 256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(factory.generateSecret(keySpec).getEncoded(), "AES");

            IvParameterSpec iv = new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(cipherMode, secretKeySpec, iv);
            return cipher;
        } catch (Exception ex) {
            // possible exceptions are all related to missing algorithms
            throw new RuntimeException(ex);
        }
    }

    protected synchronized void clearMasterPasswordIfRequired() {
        incrementAndCheckMPUseCount();
        startMPExpirationTimerIfNotStarted();
    }

    protected synchronized void incrementAndCheckMPUseCount() {
        if (settings.isCacheEnabled() && settings.getClearMethod() == SecureStorageSettings.CacheClearMethod.MAX_USES) {
            mpUseCount++;
            if (mpUseCount > settings.getClearValue()) {
                synchronized (lockMasterPasswordReset) {
                    resetMPUseCount();
                    clearCachedMasterPassword();
                    Logger.getAnonymousLogger().info("Reset mp (use count)");
                }
            }
        }
    }

    protected synchronized void resetMPUseCount() {
        mpUseCount = 0;
    }

    protected synchronized void startMPExpirationTimerIfNotStarted() {
        if (settings.isCacheEnabled() && settings.getClearMethod() == SecureStorageSettings.CacheClearMethod.EXPIRATION_TIME &&
                mpExpirationTimerTask == null) {
            synchronized (lockMasterPasswordReset) {
                mpExpirationTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        clearCachedMasterPassword();
                        Logger.getAnonymousLogger().info("Reset mp (timer)");
                    }
                };
                timer = new Timer();
                timer.schedule(mpExpirationTimerTask, settings.getClearValue() * 60 * 1000);
            }
        }
    }

    protected synchronized void stopMPExpirationTimer() {
        if (mpExpirationTimerTask != null) {
            mpExpirationTimerTask.cancel();
            mpExpirationTimerTask = null;
        }
    }

    protected synchronized void restartMPExpirationTimer() {
        stopMPExpirationTimer();
        startMPExpirationTimerIfNotStarted();
    }

    public void cleanup() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
