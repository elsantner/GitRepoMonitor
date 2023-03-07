package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.TestUtils;
import at.aau.ainf.gitrepomonitor.core.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class SecureFileStorageTest {

    static FileManager fileManagerMock = mock(FileManager.class);
    static SecureFileStorageTestable secStorage;

    @BeforeAll
    static void setup() {
        secStorage = new SecureFileStorageTestable();
        secStorage.setFileManager(fileManagerMock);
    }

    /**
     * Test if D(E(m,k),k) == m
     */
    @Test
    void testCipher() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        String plaintext = "someText";
        char[] secretKey = "someSecretKey".toCharArray();
        String ciphertext = secStorage.encrypt(plaintext, secretKey);
        assertEquals(secStorage.decrypt(ciphertext, secretKey), plaintext);
    }

    @Test
    void testCipherBytes() throws Exception {
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        String plaintext = "someText";
        char[] secretKey = "someSecretKey".toCharArray();
        byte[] ciphertext = secStorage.encryptToBytes(plaintext, secretKey);
        assertEquals(secStorage.decryptFromBytes(ciphertext, secretKey), plaintext);
    }

    private AuthenticationCredentials createSomeCredentials() {
        return new HttpsCredentials("Username", "password".toCharArray());
    }

    private void setSomeCachedMP() {
        secStorage.setCachedMasterPassword(Utils.sha3_256("MP".toCharArray()));
    }

    @Test
    void testStoreMPNotCached() {
        assertThrows(SecurityException.class, () -> {
            secStorage.store(createSomeCredentials());
        });
    }

    @Test
    void testStoreCredsCleared() throws AuthenticationException {
        setSomeCachedMP();
        secStorage.setDisableMPCheck(true);
        HttpsCredentials credentials = new HttpsCredentials("Username", "password".toCharArray());
        secStorage.store(credentials);
        assertTrue(TestUtils.isCleared(credentials.getPassword()));
    }

    @Test
    void testSHA() {
        char[] hash = Utils.sha3_256("test".toCharArray());
        assertEquals("36f028580bb02cc8272a9a020f4200e346e276ae664e45ee80745574e2f5ab80", String.valueOf(hash));
    }

    @AfterEach
    void teardown() {
        // remove test file
        SecureFileStorageTestable secStorage = new SecureFileStorageTestable();
        secStorage.removeCredentialTestFile();
        // reset MP correctness bypass
        secStorage.setDisableMPCheck(false);
    }
}
