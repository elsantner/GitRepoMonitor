package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.eclipse.jgit.transport.CredentialsProvider;

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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.logging.Logger;


public abstract class SecureStorage {

    // salt for AES ciphers
    protected static final String SALT = "3JN3DXVqcVxzxtZK";

    protected XmlMapper mapper;
    protected char[] masterPassword;
    protected SecureStorageSettings settings;
    protected File fileSettings = new File(Utils.getProgramHomeDir() + "settings.xml");

    protected SecureStorage() {
        this.mapper = XmlMapper.xmlBuilder().build();
        loadSettings();
    }

    public static SecureStorage getImplementation() {
        return SecureKeyringStorage.getInstance();
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
        this.settings.setCacheEnabled(cacheMasterPassword);
        if (!cacheMasterPassword) {
            masterPassword = null;
        }
        persistSettings();
    }

    public boolean isMasterPasswordCacheEnabled() {
        return this.settings.isCacheEnabled();
    }

    /**
     * @return True, if the master password is currently cached.
     */
    public boolean isMasterPasswordCached() {
        return masterPassword != null;
    }

    public void clearCachedMasterPassword() {
        clearCharArray(masterPassword);
        masterPassword = null;
    }

    protected void clearCharArray(char[] a) {
        Arrays.fill(a, (char) 0);
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
        char[] hashedPW = sha3_256(mp);
        clearCharArray(mp);
        return hashedPW;
    }

    /**
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    public abstract boolean isMasterPasswordSet();

    public abstract void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException;

    public abstract void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException;

    public abstract void storeHttpsCredentials(char[] masterPW, UUID repoID,
                                         String httpsUsername, char[] httpsPassword) throws IOException;

    public abstract void storeHttpsCredentials(UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException;

    public abstract void deleteHttpsCredentials(char[] masterPW, UUID repoID) throws IOException;

    public abstract void deleteHttpsCredentials(UUID repoID) throws IOException;

    public abstract HttpsCredentials getHttpsCredentials(char[] masterPW, UUID repoID) throws IOException;

    public abstract HttpsCredentials getHttpsCredentials(UUID repoID) throws IOException;

    public abstract CredentialsProvider getHttpsCredentialProvider(char[] masterPW, UUID repoID) throws IOException;

    public abstract CredentialsProvider getHttpsCredentialProvider(UUID repoID) throws IOException;

    public abstract Map<UUID, CredentialsProvider> getHttpsCredentialProviders(char[] masterPW, List<RepositoryInformation> repos) throws IOException;

    public abstract Map<UUID, CredentialsProvider> getHttpsCredentialProviders(List<RepositoryInformation> repos) throws IOException;

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

    protected synchronized char[] sha3_256(char[] m) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(toBytes(m));
            return bytesToHex(hash).toCharArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexStr = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexStr.append('0');
            }
            hexStr.append(hex);
        }
        return hexStr.toString();
    }

    private byte[] toBytes(char[] chars) {
        CharBuffer bufChar = CharBuffer.wrap(chars);
        ByteBuffer bufByte = StandardCharsets.UTF_8.encode(bufChar);
        byte[] bytes = Arrays.copyOfRange(bufByte.array(),
                bufByte.position(), bufByte.limit());
        Arrays.fill(bufByte.array(), (byte) 0);
        return bytes;
    }

    public abstract boolean isIntact(List<RepositoryInformation> authRequiredRepos);

    private void persistSettings() {
        try {
            mapper.writeValue(fileSettings, settings);
            Logger.getAnonymousLogger().info("Wrote settings to " + fileSettings.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadSettings() {
        try {
            settings = mapper.readValue(fileSettings, new TypeReference<SecureStorageSettings>(){});
        } catch (IOException e) {
            e.printStackTrace();
            settings = new SecureStorageSettings();
        }
    }
}
