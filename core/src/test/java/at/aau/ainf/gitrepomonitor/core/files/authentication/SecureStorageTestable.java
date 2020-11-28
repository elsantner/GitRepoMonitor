package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.Utils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.IOException;

public class SecureStorageTestable extends SecureStorage {

    public static String CREDENTIALS_FILENAME = "credsTest";

    protected String getCredentialsFilename() {
        return CREDENTIALS_FILENAME;
    }

    public String encrypt(String plaintext, String key) {
        return super.encrypt(plaintext, key);
    }

    public String decrypt(String plaintext, String key) throws BadPaddingException, IllegalBlockSizeException {
        return super.decrypt(plaintext, key);
    }

    public String decryptFromBytes(byte[] ciphertext, String key) throws BadPaddingException, IllegalBlockSizeException {
        return super.decryptFromBytes(ciphertext, key);
    }

    public byte[] encryptToBytes(String plaintext, String key) {
        return super.encryptToBytes(plaintext, key);
    }

    public CredentialWrapper readCredentials(String masterPW) throws IOException {
        return super.readCredentials(masterPW);
    }

    public void writeCredentials(CredentialWrapper credentials, String masterPW) throws IOException {
        super.writeCredentials(credentials, masterPW);
    }

    public void removeCredentialTestFile() {
        new File(Utils.getProgramHomeDir() + getCredentialsFilename()).delete();
    }
}
