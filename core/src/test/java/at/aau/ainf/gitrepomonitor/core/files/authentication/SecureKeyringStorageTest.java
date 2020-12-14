package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class SecureKeyringStorageTest {

    @Test
    public void testSetMasterPW() throws Exception {
        SecureKeyringStorageTestable secStorage = new SecureKeyringStorageTestable();
        secStorage.enableMasterPasswordCache(true);
        assertFalse(secStorage.isMasterPasswordSet());
        secStorage.setMasterPassword("someMasterPW".toCharArray());
        assertTrue(secStorage.isMasterPasswordSet());
        List<RepositoryInformation> repos = Collections.singletonList(new RepositoryInformation());
        repos.get(0).setAuthMethod(RepositoryInformation.AuthMethod.HTTPS);
        UUID repoID = repos.get(0).getID();

        secStorage.storeHttpsCredentials("someMasterPW".toCharArray(), repoID,
                "user", "pass".toCharArray());

        secStorage.updateMasterPassword("someMasterPW".toCharArray(), "newPW".toCharArray(), repos);
        assertTrue(secStorage.isMasterPasswordSet());
        assertArrayEquals(secStorage.sha3_256("newPW".toCharArray()), secStorage.getCachedMasterPassword());

        HttpsCredentials credentials = secStorage.getHttpsCredentials("newPW".toCharArray(), repoID);
        assertEquals("user", credentials.getUsername());
        assertArrayEquals("pass".toCharArray(), credentials.getPassword());
    }

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
    public void removeTestFile() throws PasswordAccessException {
        SecureKeyringStorageTestable secStorage = new SecureKeyringStorageTestable();
        secStorage.removeCredentialTestStorage();
    }
}
