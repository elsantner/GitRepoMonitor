package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.StoragePath;
import at.aau.ainf.gitrepomonitor.core.files.Utils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.IOException;

public class SecureFileStorageTestable extends SecureFileStorage {

    public static String CREDENTIALS_FILENAME = "credsTest";

    protected String getCredentialsFilename() {
        return CREDENTIALS_FILENAME;
    }

    public String encrypt(String plaintext, char[] key, String salt) {
        return super.encrypt(plaintext, key, salt);
    }

    public String decrypt(String ciphertext, char[] key, String salt) throws BadPaddingException, IllegalBlockSizeException {
        return super.decrypt(ciphertext, key, salt);
    }

    public String decryptFromBytes(byte[] ciphertext, char[] key, String salt) throws BadPaddingException, IllegalBlockSizeException {
        return super.decryptFromBytes(ciphertext, key, salt);
    }

    public byte[] encryptToBytes(String plaintext, char[] key, String salt) {
        return super.encryptToBytes(plaintext, key, salt);
    }

    public void removeCredentialTestFile() {
        new File(StoragePath.getCurrentPath() + getCredentialsFilename()).delete();
    }

    public char[] getCachedMasterPassword() {
        return this.masterPassword;
    }
}
