package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * SSH session factory to configure a session for a specific private key file.
 */
public final class CustomSshSessionFactory extends SshdSessionFactory {

    public static Path sshDir;
    private final Path privateKeyFile;

    public CustomSshSessionFactory(String sslKeyPath) {
        privateKeyFile = Path.of(sslKeyPath);
        sshDir = privateKeyFile.getParent();
    }

    @Override
    public File getSshDirectory() {
        try {
            return Files.createDirectories(sshDir).toFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Return paths to private key files
    @Override
    protected List<Path> getDefaultIdentities(File sshDir) {
        return Collections.singletonList(privateKeyFile);
    }

}