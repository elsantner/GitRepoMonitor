package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Utils;
import com.github.javakeyring.PasswordAccessException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.IOException;

public class SecureKeyringStorageTestable extends SecureKeyringStorage {

    public static String SERVICE_NAME = "credsTest";

    protected String getServiceName() {
        return SERVICE_NAME;
    }

    public void removeCredentialTestStorage() throws PasswordAccessException {
        this.keyring.deletePassword(SERVICE_NAME, MP_SET);
    }

    public synchronized char[] sha3_256(char[] m) {
        return super.sha3_256(m);
    }

    public char[] getCachedMasterPassword() {
        return this.masterPassword;
    }
}
