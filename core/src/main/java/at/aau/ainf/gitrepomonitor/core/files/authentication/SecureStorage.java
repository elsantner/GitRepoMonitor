package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.eclipse.jgit.transport.CredentialsProvider;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.*;


/**
 * The idea of this credential storage system is as follows:
 * - The user can store repo credentials by inputting a master password
 * - The repository carries information whether it has associated credentials or not (in plaintext)
 * - Every repos credentials are stored in a separate file
 *    - The filename is derived from the ID of a repo and the master password
 *    - If the repo has associated credentials but there is no such file, master password was wrong
 *
 * PROBLEM: How to ensure the master pw is always the same?
 *    - Maybe hashed and stored in master file?
 *    - Or use just one single file --> less secure...
 */
public class SecureStorage {

    private static SecureStorage instance;
    // salt for AES ciphers
    private static final String SALT = "3JN3DXVqcVxzxtZK";
    private static final String CREDENTIALS_FILENAME = "creds";

    private XmlMapper mapper;
    private char[] masterPassword;
    private boolean cacheMasterPassword = false;

    public static synchronized SecureStorage getInstance() {
        if (instance == null) {
            instance = new SecureStorage();
        }
        return instance;
    }

    protected SecureStorage() {
        this.mapper = XmlMapper.xmlBuilder().build();
    }

    protected String getCredentialsFilename() {
        return CREDENTIALS_FILENAME;
    }

    /**
     * Set whether to cache the master password.
     * If enabled, once any method has been called using a valid master password,
     * it is stored internally and subsequent functions requiring the master password
     * can be called without it.
     * @param cacheMasterPassword True, if master password should be cached.
     */
    public synchronized void setCacheMasterPassword(boolean cacheMasterPassword) {
        this.cacheMasterPassword = cacheMasterPassword;
        if (!cacheMasterPassword) {
            masterPassword = null;
        }
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

    private void clearCharArray(char[] a) {
        Arrays.fill(a, (char) 0);
    }

    private synchronized void cacheMasterPasswordIfEnabled(char[] masterPassword) {
        if (cacheMasterPassword) {
            this.masterPassword = Arrays.copyOf(masterPassword, masterPassword.length);
        }
    }

    private void throwIfMasterPasswordNotCached() {
        if (!isMasterPasswordCached()) {
            throw new SecurityException("master password not cached but required");
        }
    }

    private char[] getCachedMasterPasswordHash(char[] mp) {
        if (mp == null) {
            throwIfMasterPasswordNotCached();
            return masterPassword;
        }
        char[] hashedPW = sha3_256(mp);
        clearCharArray(mp);
        return hashedPW;
    }

    /**
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    public boolean isMasterPasswordSet() {
        File credentialsFile = new File(Utils.getProgramHomeDir() + getCredentialsFilename());
        return (credentialsFile.exists() && !credentialsFile.isDirectory());
    }

    public void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException {
        if (isMasterPasswordSet()) {
            throw new AuthenticationException("master password was already set");
        }
        char[] hashedPW = sha3_256(masterPW);
        writeCredentials(new CredentialWrapper(), hashedPW);
        clearCharArray(masterPW);
        clearCharArray(hashedPW);
    }

    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException {
        if (!isMasterPasswordSet()) {
            throw new AuthenticationException("master password was not set before");
        }
        char[] hashedCurrentPW = sha3_256(currentMasterPW);
        char[] hashedNewPW = sha3_256(newMasterPW);
        CredentialWrapper wrapper = readCredentials(hashedCurrentPW);
        writeCredentials(wrapper, hashedNewPW);
        clearCharArray(currentMasterPW);
        clearCharArray(newMasterPW);
        clearCharArray(hashedCurrentPW);
        clearCharArray(hashedNewPW);
    }

    public void storeHttpsCredentials(char[] masterPW, UUID repoID,
                                         String httpsUsername, char[] httpsPassword) throws IOException {

        masterPW = getCachedMasterPasswordHash(masterPW);
        CredentialWrapper allCredentials = readCredentials(masterPW);
        HttpsCredentials newCredentials = new HttpsCredentials(repoID, httpsUsername, httpsPassword);
        allCredentials.putCredentials(newCredentials);
        writeCredentials(allCredentials, masterPW);

        cacheMasterPasswordIfEnabled(masterPW);
        clearCharArray(masterPW);
        clearCharArray(httpsPassword);
    }

    public void storeHttpsCredentials(UUID repoID, String httpsUsername, char[] httpsPassword) throws IOException {
        throwIfMasterPasswordNotCached();
        storeHttpsCredentials(masterPassword, repoID, httpsUsername, httpsPassword);
    }

    public HttpsCredentials getHttpsCredentials(char[] masterPW, UUID repoID) throws IOException {
        masterPW = getCachedMasterPasswordHash(masterPW);
        return getHttpsCredentialsHashed(masterPW, repoID);
    }

    private HttpsCredentials getHttpsCredentialsHashed(char[] masterPWHash, UUID repoID) throws IOException {
        CredentialWrapper allCredentials = readCredentials(masterPWHash);
        HttpsCredentials credentials = allCredentials.getCredentials(repoID);
        cacheMasterPasswordIfEnabled(masterPWHash);
        clearCharArray(masterPWHash);
        return credentials;
    }

    public HttpsCredentials getHttpsCredentials(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentials(masterPassword, repoID);
    }

    public CredentialsProvider getHttpsCredentialProvider(char[] masterPW, UUID repoID) throws IOException {
        masterPW = getCachedMasterPasswordHash(masterPW);
        char[] masterPwCopy = Arrays.copyOf(masterPW, masterPW.length);
        HttpsCredentials credentials = getHttpsCredentialsHashed(masterPW, repoID);
        CredentialsProvider cp = new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
        cacheMasterPasswordIfEnabled(masterPwCopy);
        clearCharArray(masterPwCopy);
        return cp;
    }

    public CredentialsProvider getHttpsCredentialProvider(UUID repoID) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentialProvider(masterPassword, repoID);
    }

    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(char[] masterPW, List<RepositoryInformation> repos) throws IOException {
        masterPW = getCachedMasterPasswordHash(masterPW);
        Map<UUID, CredentialsProvider> map = new HashMap<>();
        CredentialWrapper allCredentials = readCredentials(masterPW);
        for (HttpsCredentials credentials : allCredentials.getHttpsCredentials()) {
            map.put(credentials.getRepoID(),
                    new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword()));
        }
        for (RepositoryInformation repo : repos) {
            if (!repo.isAuthenticated()) {
                map.remove(repo.getID());
            }
        }
        cacheMasterPasswordIfEnabled(masterPW);
        clearCharArray(masterPW);
        return map;
    }

    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(List<RepositoryInformation> repos) throws IOException {
        throwIfMasterPasswordNotCached();
        return getHttpsCredentialProviders(masterPassword, repos);
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
}
