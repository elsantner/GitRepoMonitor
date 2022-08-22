package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public class SSHPasswordProvider implements KeyPasswordProvider {

    private char[] passphrase;

    public SSHPasswordProvider(byte[] passphrase) {
        String encodedPassphrase = new String(passphrase, StandardCharsets.UTF_8);
        this.passphrase = encodedPassphrase.toCharArray();
    }

    @Override
    public char[] getPassphrase(URIish uri, int attempt) throws IOException {
        return passphrase;
    }

    @Override
    public void setAttempts(int maxNumberOfAttempts) {

    }

    @Override
    public boolean keyLoaded(URIish uri, int attempt, Exception error) throws IOException, GeneralSecurityException {
        return true;
    }
}
