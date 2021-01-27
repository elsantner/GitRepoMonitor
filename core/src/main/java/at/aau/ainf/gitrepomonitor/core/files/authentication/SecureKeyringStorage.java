package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.git.SSLTransportConfigCallback;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.KeyringStorageType;
import com.github.javakeyring.PasswordAccessException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.naming.AuthenticationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecureKeyringStorage extends SecureStorage {

    private static SecureKeyringStorage instance;
    protected static final String SERVICE = "gitrepomonitor";
    protected static final String MP_SET = "mpset";

    public static synchronized SecureKeyringStorage getInstance() {
        if (instance == null) {
            instance = new SecureKeyringStorage();
        }
        return instance;
    }

    protected Keyring keyring;
    private boolean isSupported = true;

    protected SecureKeyringStorage() {
        super();
        try {
            keyring = Keyring.create();
        } catch (BackendNotSupportedException ex) {
            isSupported = false;
        }
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public boolean isMasterPasswordSet() {
        return false;
    }

    @Override
    public void setMasterPassword(char[] masterPW) throws AuthenticationException, IOException {

    }

    @Override
    public void updateMasterPassword(char[] currentMasterPW, char[] newMasterPW) throws AuthenticationException, IOException {

    }

    @Override
    public void store(char[] masterPW, AuthenticationInformation authInfo) throws AuthenticationException {

    }

    @Override
    public void store(AuthenticationInformation authInfo) throws AuthenticationException {

    }

    @Override
    public void update(char[] masterPW, AuthenticationInformation authInfo) throws AuthenticationException {

    }

    @Override
    public void update(AuthenticationInformation authInfo) throws AuthenticationException {

    }

    @Override
    public void delete(UUID id) {

    }

    @Override
    public AuthenticationInformation get(char[] masterPW, UUID id) throws AuthenticationException {
        return null;
    }

    @Override
    public AuthenticationInformation get(UUID id) throws AuthenticationException {
        return null;
    }

    @Override
    public void resetMasterPassword() throws IOException {

    }

    protected String getServiceName() {
        return SERVICE;
    }

    public KeyringStorageType getStorageType() {
        return keyring.getKeyringStorageType();
    }

}
