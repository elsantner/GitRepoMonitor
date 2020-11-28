package at.aau.ainf.gitrepomonitor.core.files.authentication;

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
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


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
     * Checks whether or not a master password was set by the user.
     * @return True, if a master password was already set
     */
    public boolean isMasterPasswordSet() {
        File credentialsFile = new File(Utils.getProgramHomeDir() + getCredentialsFilename());
        return (credentialsFile.exists() && !credentialsFile.isDirectory());
    }

    public void setMasterPassword(String masterPW) throws AuthenticationException, IOException {
        if (isMasterPasswordSet()) {
            throw new AuthenticationException("master password was already set");
        }
        writeCredentials(new CredentialWrapper(), masterPW);
    }

    public void updateMasterPassword(String currentMasterPW, String newMasterPW) throws AuthenticationException, IOException {
        if (!isMasterPasswordSet()) {
            throw new AuthenticationException("master password was not set before");
        }
        CredentialWrapper wrapper = readCredentials(currentMasterPW);
        writeCredentials(wrapper, newMasterPW);
    }

    public void storeHttpsCredentials(String masterPW, UUID repoID,
                                         String httpsUsername, String httpsPassword) throws IOException {

        CredentialWrapper allCredentials = readCredentials(masterPW);
        HttpsCredentials newCredentials = new HttpsCredentials(repoID, httpsUsername, httpsPassword);
        allCredentials.putCredentials(newCredentials);
        writeCredentials(allCredentials, masterPW);
    }

    public HttpsCredentials getHttpsCredentials(String masterPW, UUID repoID) throws IOException {
        CredentialWrapper allCredentials = readCredentials(masterPW);
        return allCredentials.getCredentials(repoID);
    }

    public CredentialsProvider getHttpsCredentialProvider(String masterPW, UUID repoID) throws IOException {
        HttpsCredentials credentials = getHttpsCredentials(masterPW, repoID);
        return new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword());
    }

    public Map<UUID, CredentialsProvider> getHttpsCredentialProviders(String masterPW) throws IOException {
        Map<UUID, CredentialsProvider> map = new HashMap<>();
        CredentialWrapper allCredentials = readCredentials(masterPW);
        for (HttpsCredentials credentials : allCredentials.getHttpsCredentials()) {
            map.put(credentials.getRepoID(),
                    new UsernamePasswordCredentialsProvider(credentials.getUsername(), credentials.getPassword()));
        }
        return map;
    }

    protected CredentialWrapper readCredentials(String masterPW) throws IOException {
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

    protected void writeCredentials(CredentialWrapper credentials, String masterPW) throws IOException {
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

    protected byte[] encryptToBytes(String plaintext, String key) {
        try {
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    protected String decryptFromBytes(byte[] ciphertext, String key) throws BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(ciphertext));
    }

    protected String encrypt(String plaintext, String key) {
        try {
            Cipher cipher = getCipherInstantiation(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // exceptions should not happen (block size & padding are highly dynamic here)
            throw new RuntimeException(e);
        }
    }

    protected String decrypt(String ciphertext, String key) throws BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = getCipherInstantiation(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)));
    }

    private Cipher getCipherInstantiation(int cipherMode, String key) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(key.toCharArray(), SALT.getBytes(), 65536, 256);
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
}
