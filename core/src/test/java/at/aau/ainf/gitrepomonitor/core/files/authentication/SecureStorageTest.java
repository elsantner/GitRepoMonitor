package at.aau.ainf.gitrepomonitor.core.files.authentication;

import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class SecureStorageTest {

    /**
     * Test if D(E(m,k),k) == m
     */
    @Test
    public void testCipher() throws Exception {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        String plaintext = "someText";
        String secretKey = "someSecretKey";
        String ciphertext = secStorage.encrypt(plaintext, secretKey);
        assertEquals(secStorage.decrypt(ciphertext, secretKey), plaintext);
    }

    @Test
    public void testCipherBytes() throws Exception {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        String plaintext = "someText";
        String secretKey = "someSecretKey";
        byte[] ciphertext = secStorage.encryptToBytes(plaintext, secretKey);
        assertEquals(secStorage.decryptFromBytes(ciphertext, secretKey), plaintext);
    }

    @Test
    public void testRead() throws Exception {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "username1", "pw1"));
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "user2", "pw2"));
        secStorage.writeCredentials(originalWrapper, "someMasterSecret");
        CredentialWrapper loadedWrapper = secStorage.readCredentials("someMasterSecret");

        assertEquals(originalWrapper.getHttpsCredentials().size(), loadedWrapper.getHttpsCredentials().size());
    }

    @Test(expected = SecurityException.class)
    public void testReadWrongPassword() throws Exception {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "username1", "pw1"));

        secStorage.writeCredentials(originalWrapper, "someMasterSecret");
        secStorage.readCredentials("wrongMasterSecret");
    }

    @Test
    public void testSetMasterPW() throws Exception {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        assertFalse(secStorage.isMasterPasswordSet());
        secStorage.setMasterPassword("someMasterPW");
        assertTrue(secStorage.isMasterPasswordSet());
        secStorage.updateMasterPassword("someMasterPW", "newPW");
        assertTrue(secStorage.isMasterPasswordSet());
    }

    @After
    public void removeTestFile() {
        SecureStorageTestable secStorage = new SecureStorageTestable();
        secStorage.removeCredentialTestFile();
    }
}
