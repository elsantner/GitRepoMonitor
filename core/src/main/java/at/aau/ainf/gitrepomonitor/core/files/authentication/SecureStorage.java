package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Settings;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.AuthenticationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.logging.Logger;


public abstract class SecureStorage {

    protected static Settings settings;
    protected static XmlMapper mapper;

    protected char[] masterPassword;
    protected int mpUseCount = 0;
    protected Timer timer;
    protected TimerTask mpExpirationTimerTask;
    protected final Object lockMasterPasswordReset = new Object();

    static {
        settings = Settings.getSettings();
        mapper = XmlMapper.xmlBuilder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
    }

    /**
     * Gets the instance of the preferred storage type set in the settings.
     * Only SecureFileStorage is available at the moment.
     * @return Preferred (and supported) secure storage instance.
     */
    public static SecureStorage getImplementation() {
        return SecureFileStorage.getInstance();
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
        Settings.persist();
    }

    public boolean isMasterPasswordCacheEnabled() {
        return settings.isCacheEnabled();
    }

    public synchronized void setMasterPasswordCacheMethod(Settings.CacheClearMethod method, Integer value) {
        settings.setClearMethod(method);
        settings.setClearValue(value);
        Settings.persist();
        // reset mp cache & clearing mechanisms
        resetMPUseCount();
        stopMPExpirationTimer();
        clearCachedMasterPassword();
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

    public abstract void store(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException;

    public abstract void store(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException;

    public abstract void store(AuthenticationCredentials authInfo) throws AuthenticationException;

    public abstract void update(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException;

    public abstract void update(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException;

    public abstract void update(AuthenticationCredentials authInfo) throws AuthenticationException;

    public abstract void delete(UUID id);

    public abstract AuthenticationCredentials get(char[] masterPW, UUID id) throws AuthenticationException;

    public abstract Map<UUID, AuthenticationCredentials> get(char[] masterPW, Collection<UUID> ids) throws AuthenticationException;

    public abstract AuthenticationCredentials get(UUID id) throws AuthenticationException;

    public abstract void resetMasterPassword() throws IOException;

    /**
     * Encrypt plaintext using AES-256 with added salt.
     * The randomly generated IV (16 bytes) is prepended to the resulting byte array.
     * @param plaintext Plaintext to encrypt
     * @param key Key used for encryption
     * @param salt Salt used for PBEKeySpec (has to be provided again for decryption)
     * @return Ciphertext as byte array (prepended by 16 byte random IV)
     */
    protected byte[] encryptToBytes(String plaintext, char[] key, String salt) {
        try {
            SecureRandom randomSecureRandom = new SecureRandom();
            byte[] iv = new byte[16];
            randomSecureRandom.nextBytes(iv);
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key, salt, new IvParameterSpec(iv));

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] completeCipher = new byte[iv.length+cipherBytes.length];
            System.arraycopy(iv, 0, completeCipher, 0, iv.length);
            System.arraycopy(cipherBytes, 0, completeCipher, iv.length, cipherBytes.length);

            return completeCipher;
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256 with added salt.
     * The IV (16 bytes) has to prepended to the ciphertext (i.e. the first 16 bytes of cipherBytes have to be the IV).
     * @param cipherBytes Ciphertext to decrypt
     * @param key Key used for decryption
     * @param salt Salt used for PBEKeySpec (has to be the same as used for encryption)
     * @return Plaintext
     */
    protected String decryptFromBytes(byte[] cipherBytes, char[] key, String salt) throws BadPaddingException, IllegalBlockSizeException {
        byte[] iv = new byte[16];
        System.arraycopy(cipherBytes, 0, iv, 0, 16);
        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key, salt, new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherBytes, 16, cipherBytes.length-16));
    }

    /**
     * Encrypt plaintext using AES-256 with added salt.
     * The randomly generated IV (16 bytes) is prepended to the resulting ciphertext (in Base64 format).
     * @param plaintext Plaintext to encrypt
     * @param key Key used for encryption
     * @param salt Salt used for PBEKeySpec (has to be provided again for decryption)
     * @return Ciphertext as Base64 String (prepended by 16 byte random IV)
     */
    protected String encrypt(String plaintext, char[] key, String salt) {
        try {
            return Base64.getEncoder().encodeToString(encryptToBytes(plaintext, key, salt));
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256 with added salt.
     * The IV (16 bytes) has to prepended to the ciphertext (i.e. the first 16 bytes of ciphertext have to be the IV).
     * @param ciphertext Ciphertext to decrypt in Base64 format
     * @param key Key used for decryption
     * @param salt Salt used for PBEKeySpec (has to be the same as used for encryption)
     * @return Plaintext
     */
    protected String decrypt(String ciphertext, char[] key, String salt) throws BadPaddingException, IllegalBlockSizeException {
        return decryptFromBytes(Base64.getDecoder().decode(ciphertext), key, salt);
    }

    private Cipher getCipherInstantiation(int cipherMode, char[] key, String salt, IvParameterSpec ivParams) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(key, salt.getBytes(), 65536, 256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(factory.generateSecret(keySpec).getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(cipherMode, secretKeySpec, ivParams);
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
        if (settings.isCacheEnabled() && settings.getClearMethod() == Settings.CacheClearMethod.MAX_USES) {
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
        if (settings.isCacheEnabled() && settings.getClearMethod() == Settings.CacheClearMethod.EXPIRATION_TIME &&
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
