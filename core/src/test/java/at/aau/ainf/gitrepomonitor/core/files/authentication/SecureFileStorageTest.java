package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Utils;
import org.junit.After;
import org.junit.Test;

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
        String salt = "afdadhsfh";
        String ciphertext = secStorage.encrypt(plaintext, secretKey, salt);
        assertEquals(secStorage.decrypt(ciphertext, secretKey, salt), plaintext);
    }

    @Test
    public void testCipherBytes() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        String plaintext = "someText";
        char[] secretKey = "someSecretKey".toCharArray();
        String salt = "afdadhsfh";
        byte[] ciphertext = secStorage.encryptToBytes(plaintext, secretKey, salt);
        assertEquals(secStorage.decryptFromBytes(ciphertext, secretKey, salt), plaintext);
    }

    /*@Test
    public void testRead() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "username1", "pw1".toCharArray()));
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0), "user2", "pw2".toCharArray()));
        secStorage.writeCredentials(originalWrapper, Utils.sha3_256("someMasterSecret".toCharArray()));
        CredentialWrapper loadedWrapper = secStorage.readCredentials(Utils.sha3_256("someMasterSecret".toCharArray()));

        assertEquals(originalWrapper.getHttpsCredentials().size(), loadedWrapper.getHttpsCredentials().size());
    }

    @Test(expected = SecurityException.class)
    public void testReadWrongPassword() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        CredentialWrapper originalWrapper = new CredentialWrapper();
        originalWrapper.putCredentials(new HttpsCredentials(new UUID(0, 0),
                "username1", "pw1".toCharArray()));

        secStorage.writeCredentials(originalWrapper, Utils.sha3_256("someMasterSecret".toCharArray()));
        secStorage.readCredentials(Utils.sha3_256("wrongMasterSecret".toCharArray()));
    }

    @Test
    public void testSetMasterPW() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        secStorage.enableMasterPasswordCache(true);
        assertFalse(secStorage.isMasterPasswordSet());
        secStorage.setMasterPassword("someMasterPW".toCharArray());
        assertTrue(secStorage.isMasterPasswordSet());
        UUID repoID = UUID.randomUUID();
        secStorage.storeHttpsCredentials("someMasterPW".toCharArray(), repoID,
                "user", "pass".toCharArray());

        secStorage.updateMasterPassword("someMasterPW".toCharArray(), "newPW".toCharArray());
        assertTrue(secStorage.isMasterPasswordSet());
        assertArrayEquals(Utils.sha3_256("newPW".toCharArray()), secStorage.getCachedMasterPassword());

        HttpsCredentials credentials = secStorage.getHttpsCredentials("newPW".toCharArray(), repoID);
        assertEquals("user", credentials.getUsername());
        assertArrayEquals("pass".toCharArray(), credentials.getPassword());
    }*/

    @Test
    public void testSHA() {
        char[] hash = Utils.sha3_256("test".toCharArray());
        assertEquals("36f028580bb02cc8272a9a020f4200e346e276ae664e45ee80745574e2f5ab80", String.valueOf(hash));
    }

    @After
    public void removeTestFile() {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        secStorage.removeCredentialTestFile();
    }
}
