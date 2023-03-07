package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.authentication.SecureFileStorage;
import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.StoragePath;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;

public class SecureFileStorageTestable extends SecureFileStorage {

    public static String CREDENTIALS_FILENAME = "credsTest";

    private boolean disableMPCheck = false;

    protected String getCredentialsFilename() {
        return CREDENTIALS_FILENAME;
    }

    public String encrypt(String plaintext, char[] key) {
        return super.encrypt(plaintext, key);
    }

    public String decrypt(String ciphertext, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        return super.decrypt(ciphertext, key);
    }

    public String decryptFromBytes(byte[] ciphertext, char[] key) throws BadPaddingException, IllegalBlockSizeException {
        return super.decryptFromBytes(ciphertext, key);
    }

    public byte[] encryptToBytes(String plaintext, char[] key) {
        return super.encryptToBytes(plaintext, key);
    }

    public void removeCredentialTestFile() {
        new File(StoragePath.getCurrentPath() + getCredentialsFilename()).delete();
    }

    public char[] setCachedMasterPassword(char[] mp) {
        return this.masterPassword = mp;
    }

    public char[] getCachedMasterPassword() {
        return this.masterPassword;
    }

    public void setFileManager(FileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void setDisableMPCheck(boolean disableMPCheck) {
        this.disableMPCheck = disableMPCheck;
    }

    @Override
    protected boolean isMasterPasswordCorrect(char[] hashedCurrentPW) {
        if (disableMPCheck) {
            return true;
        }
        return super.isMasterPasswordCorrect(hashedCurrentPW);
    }
}
