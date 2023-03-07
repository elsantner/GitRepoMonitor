package at.aau.ainf.gitrepomonitor.core.authentication;

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

/**
 * Abstract implementation of credential access.
 * This class provides implementations for master password caching and crypto.
 */
public abstract class SecureStorage {

    private static final int LENGTH_IV = 16;
    private static final int LENGTH_SALT = 16;
    private static final int LENGTH_TOTAL = LENGTH_IV + LENGTH_SALT;

    protected static Settings settings;
    protected static XmlMapper mapper;
    // master password HASH cache
    protected char[] masterPassword;
    // operation counter for mp (reset after n uses)
    protected int mpUseCount = 0;
    // timer for mp reset
    protected Timer timer;
    protected TimerTask mpExpirationTimerTask;
    // lock to avoid timer-based mp reset during operations
    protected final Object lockMasterPasswordReset = new Object();

    static {
        // load settings
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

    /**
     * Set the method for master password caching.
     * @param method Caching method to use
     * @param value Associated value (use count or expiration time)
     */
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

    /**
     * Clear master password cache
     */
    public void clearCachedMasterPassword() {
        if (masterPassword != null) {
            Utils.clearArray(masterPassword);
            masterPassword = null;
        }
    }

    /**
     * Cache master password if caching is enabled.
     * @param masterPassword Master password to cache.
     */
    protected synchronized void cacheMasterPasswordIfEnabled(char[] masterPassword) {
        if (settings.isCacheEnabled()) {
            this.masterPassword = Arrays.copyOf(masterPassword, masterPassword.length);
        }
    }

    /**
     * Throw exception if master password is not cached.
     * @throws SecurityException If master password is not cached.
     */
    protected void throwIfMasterPasswordNotCached() throws SecurityException {
        if (!isMasterPasswordCached()) {
            throw new SecurityException("master password not cached but required");
        }
    }

    /**
     * Get master password SHA3-256 hash.
     * @param mp Master password
     * @return If {@code mp} is null and the master password is cached, get the cached hash.
     *         Else return the hash of {@code mp}.
     */
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

    /**
     * Set the master password persistently.
     * @param masterPW Master password
     * @throws AuthenticationException
     * @throws IOException
     */
    public abstract void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException;

    /**
     * Update the master password persistently.
     * @param currentMasterPW Current master password.
     * @param newMasterPW New master password.
     * @throws AuthenticationException
     * @throws IOException
     */
    public abstract void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException;

    /**
     * Store auth credentials securely.
     * @param masterPW Master password.
     * @param authInfo Auth credentials.
     * @throws AuthenticationException
     */
    public abstract void store(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException;

    /**
     * Store all auth credentials securely.
     * @param masterPW Master password.
     * @param authInfos Auth credentials.
     * @throws AuthenticationException
     */
    public abstract void store(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException;

    /**
     * Store auth credentials securely.
     * Uses cached master password.
     * @param authInfo Auth credentials.
     * @throws AuthenticationException
     */
    public abstract void store(AuthenticationCredentials authInfo) throws AuthenticationException;

    /**
     * Update auth credentials.
     * @param masterPW Master password
     * @param authInfo Auth credentials
     * @throws AuthenticationException
     */
    public abstract void update(char[] masterPW, AuthenticationCredentials authInfo) throws AuthenticationException;

    /**
     * Update all auth credentials.
     * @param masterPW Master password
     * @param authInfos Auth credentials
     * @throws AuthenticationException
     */
    public abstract void update(char[] masterPW, Collection<AuthenticationCredentials> authInfos) throws AuthenticationException;

    /**
     * Update auth credentials.
     * Uses cached master password.
     * @param authInfo Auth credentials
     * @throws AuthenticationException
     */
    public abstract void update(AuthenticationCredentials authInfo) throws AuthenticationException;

    /**
     * Delete auth credentials.
     * @param id ID of auth credentials.
     */
    public abstract void delete(UUID id);

    /**
     * Get auth credentials by ID.
     * @param masterPW Master password
     * @param id ID of auth credentials.
     * @return Auth credentials with ID.
     * @throws AuthenticationException
     */
    public abstract AuthenticationCredentials get(char[] masterPW, UUID id) throws AuthenticationException;

    /**
     * Get all auth credentials by IDs.
     * @param masterPW Master password
     * @param ids IDs of auth credentials to get
     * @return Map of all requested auth credentials by ID
     * @throws AuthenticationException
     */
    public abstract Map<UUID, AuthenticationCredentials> get(char[] masterPW, Collection<UUID> ids) throws AuthenticationException;

    /**
     * Get auth credentials by ID.
     * Uses cached master password.
     * @param id ID of auth credentials
     * @return Auth credentials with ID.
     * @throws AuthenticationException
     */
    public abstract AuthenticationCredentials get(UUID id) throws AuthenticationException;

    /**
     * Clear / reset master password.
     * @throws IOException
     */
    public abstract void resetMasterPassword() throws IOException;

    /**
     * Encrypt plaintext using AES-256 with added salt.
     * The randomly generated IV (16 bytes) is prepended to the resulting byte array.
     * @param plaintext Plaintext to encrypt
     * @param key Key used for encryption
     * @return Ciphertext as byte array (prepended by 16 byte random IV)
     */
    protected byte[] encryptToBytes(String plaintext, char[] key) {
        try {
            SecureRandom randomSecureRandom = new SecureRandom();
            byte[] iv = new byte[LENGTH_IV];
            randomSecureRandom.nextBytes(iv);
            byte[] salt = new byte[LENGTH_SALT];
            randomSecureRandom.nextBytes(salt);
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key, salt, new IvParameterSpec(iv));

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] completeCipher = new byte[LENGTH_TOTAL+cipherBytes.length];
            System.arraycopy(iv, 0, completeCipher, 0, LENGTH_IV);
            System.arraycopy(salt, 0, completeCipher, LENGTH_IV, LENGTH_SALT);
            System.arraycopy(cipherBytes, 0, completeCipher, LENGTH_TOTAL, cipherBytes.length);

            return completeCipher;
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypt ciphertext using AES-256 with added salt.
     * The IV (16 bytes) has to prepended to the ciphertext (i.e. the first 2x16 bytes of cipherBytes have to be IV and salt).
     * @param cipherBytes Ciphertext to decrypt
     * @param key Key used for decryption
     * @return Plaintext
     */
    protected String decryptFromBytes(byte[] cipherBytes, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        byte[] iv = new byte[LENGTH_IV];
        System.arraycopy(cipherBytes, 0, iv, 0, LENGTH_IV);
        byte[] salt = new byte[LENGTH_SALT];
        System.arraycopy(cipherBytes, LENGTH_IV, salt, 0, LENGTH_SALT);

        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key, salt, new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherBytes, LENGTH_TOTAL, cipherBytes.length-LENGTH_TOTAL));
    }

    /**
     * Encrypt plaintext using AES-256 with added salt.
     * The randomly generated IV (16 bytes) is prepended to the resulting ciphertext (in Base64 format).
     * @param plaintext Plaintext to encrypt
     * @param key Key used for encryption
     * @return Ciphertext as Base64 String (prepended by 16 byte random IV)
     */
    protected String encrypt(String plaintext, char[] key) {
        try {
            return Base64.getEncoder().encodeToString(encryptToBytes(plaintext, key));
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
     * @return Plaintext
     */
    protected String decrypt(String ciphertext, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        return decryptFromBytes(Base64.getDecoder().decode(ciphertext), key);
    }

    /**
     * Get AES instance with provided parameters.
     * @param cipherMode Encrypt or decrypt
     * @param key Secret Key
     * @param salt Salt
     * @param ivParams IV
     * @return AES Cipher instance in CBC mode with PKCS5 padding.
     */
    private Cipher getCipherInstantiation(int cipherMode, char[] key, byte[] salt, IvParameterSpec ivParams) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(key, salt, 65536, 256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(factory.generateSecret(keySpec).getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(cipherMode, secretKeySpec, ivParams);
            return cipher;
        } catch (Exception ex) {
            // possible exceptions are all related to missing algorithms
            throw new RuntimeException(ex);
        }
    }

    /**
     * Increment and check mp cache use count and reset if necessary.
     */
    protected synchronized void clearMasterPasswordIfRequired() {
        incrementAndCheckMPUseCount();
        startMPExpirationTimerIfNotStarted();
    }

    /**
     * If cache method is set to MAX_USES, increment use count and clear cache if necessary.
     */
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

    /**
     * Set MP use count to 0
     */
    protected synchronized void resetMPUseCount() {
        mpUseCount = 0;
    }

    /**
     * If MP reset timer is not started already and cache method is set to EXPIRATION_TIME,
     * schedule a new rest timer.
     */
    protected synchronized void startMPExpirationTimerIfNotStarted() {
        if (settings.isCacheEnabled() && settings.getClearMethod() == Settings.CacheClearMethod.EXPIRATION_TIME &&
                mpExpirationTimerTask == null) {

            mpExpirationTimerTask = new TimerTask() {
                @Override
                public void run() {
                    // clear MP cache (lock to make sure this does not happen during an operation)
                    synchronized (lockMasterPasswordReset) {
                        clearCachedMasterPassword();
                        Logger.getAnonymousLogger().info("Reset mp (timer)");
                    }
                }
            };
            timer = new Timer();
            timer.schedule(mpExpirationTimerTask, settings.getClearValue() * 60 * 1000);

        }
    }

    /**
     * Stop running MP reset timer.
     */
    protected synchronized void stopMPExpirationTimer() {
        if (mpExpirationTimerTask != null) {
            mpExpirationTimerTask.cancel();
            mpExpirationTimerTask = null;
        }
    }

    /**
     * Restart MP reset timer from 0s.
     */
    protected synchronized void restartMPExpirationTimer() {
        stopMPExpirationTimer();
        startMPExpirationTimerIfNotStarted();
    }

    /**
     * Stop running timer.
     */
    public void cleanup() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
