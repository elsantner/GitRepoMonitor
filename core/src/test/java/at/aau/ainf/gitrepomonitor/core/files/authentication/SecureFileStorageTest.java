package at.aau.ainf.gitrepomonitor.core.files.authentication;

import com.github.javakeyring.Keyring;
import org.junit.After;
import org.junit.Test;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.UUID;

import static org.junit.Assert.*;

public class SecureFileStorageTest {

    /**
     * Test if D(E(m,k),k) == m
     */
    @Test
    public void testCipher() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        String plaintext = "someText";
        char[] secretKey = "someSecretKey".toCharArray();
        String ciphertext = secStorage.encrypt(plaintext, secretKey);
        assertEquals(secStorage.decrypt(ciphertext, secretKey), plaintext);
    }

    @Test
    public void testCipherBytes() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        String plaintext = "someText";
        char[] secretKey = "someSecretKey".toCharArray();
        byte[] ciphertext = secStorage.encryptToBytes(plaintext, secretKey);
        assertEquals(secStorage.decryptFromBytes(ciphertext, secretKey), plaintext);
    }

    @Test
    public void testRead() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "username1", "pw1".toCharArray()));
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "user2", "pw2".toCharArray()));
        secStorage.writeCredentials(originalWrapper, secStorage.sha3_256("someMasterSecret".toCharArray()));
        CredentialWrapper loadedWrapper = secStorage.readCredentials(secStorage.sha3_256("someMasterSecret".toCharArray()));

        assertEquals(originalWrapper.getHttpsCredentials().size(), loadedWrapper.getHttpsCredentials().size());
    }

    @Test(expected = SecurityException.class)
    public void testReadWrongPassword() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0),
                "username1", "pw1".toCharArray()));

        secStorage.writeCredentials(originalWrapper, secStorage.sha3_256("someMasterSecret".toCharArray()));
        secStorage.readCredentials(secStorage.sha3_256("wrongMasterSecret".toCharArray()));
    }

    @Test
    public void testSetMasterPW() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        secStorage.setCacheMasterPassword(true);
        assertFalse(secStorage.isMasterPasswordSet());
        secStorage.setMasterPassword("someMasterPW".toCharArray());
        assertTrue(secStorage.isMasterPasswordSet());
        UUID repoID = UUID.randomUUID();
        secStorage.storeHttpsCredentials("someMasterPW".toCharArray(), repoID,
                "user", "pass".toCharArray());

        secStorage.updateMasterPassword("someMasterPW".toCharArray(), "newPW".toCharArray());
        assertTrue(secStorage.isMasterPasswordSet());
        assertArrayEquals(secStorage.sha3_256("newPW".toCharArray()), secStorage.getCachedMasterPassword());

        HttpsCredentials credentials = secStorage.getHttpsCredentials("newPW".toCharArray(), repoID);
        assertEquals("user", credentials.getUsername());
        assertArrayEquals("pass".toCharArray(), credentials.getPassword());
    }

    @Test
    public void testSHA() {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        char[] hash = secStorage.sha3_256("test".toCharArray());
        assertEquals("36f028580bb02cc8272a9a020f4200e346e276ae664e45ee80745574e2f5ab80", String.valueOf(hash));
    }

    /*private static final String SALT = "3JN3DXVqcVxzxtZK";

    @Test
    public void testWinCertStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableEntryException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        char[] keyStorePassword = "123abc".toCharArray();
        ks.load(null, keyStorePassword);

        char[] keyPassword = "789xyz".toCharArray();
        KeyStore.ProtectionParameter entryPassword =
                new KeyStore.PasswordProtection(keyPassword);

        byte[] encoded = "someDate".getBytes();
        SecretKey secretKey = new SecretKeySpec(encoded, "AES");
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
        ks.setEntry("keyAlias", secretKeyEntry, entryPassword);


        KeyStore.SecretKeyEntry privateKeyEntry = (KeyStore.SecretKeyEntry)
                ks.getEntry("keyAlias", entryPassword);

        System.out.println(new String(privateKeyEntry.getSecretKey().getEncoded()));
    }*/

    @Test
    public void testKeyringAPI() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();

        Keyring keyring = Keyring.create();
        String serviceName = "gitrepomonitor";
        String accountName = "test";
        keyring.setPassword(serviceName, accountName, secStorage.encrypt("myPassword", "masterPW".toCharArray()));
        String password = keyring.getPassword(serviceName, accountName);
        System.out.println(secStorage.decrypt(password, "masterPW".toCharArray()));
    }

    @After
    public void removeTestFile() {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        secStorage.removeCredentialTestFile();
    }
}
