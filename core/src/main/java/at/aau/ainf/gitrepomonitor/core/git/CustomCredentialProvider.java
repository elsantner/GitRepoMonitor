package at.aau.ainf.gitrepomonitor.core.git;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Credentials provider to provide a passphrase for a private key
 */
public class CustomCredentialProvider extends CredentialsProvider {

    private final String passphrase;

    public CustomCredentialProvider(String passphrase) {
        this.passphrase = passphrase;
    }

    @Override
    public boolean isInteractive() { return false; }

    @Override
    public boolean supports(CredentialItem... items) { return true; }

    // Return passphrase in the required format
    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {

        for (CredentialItem item : items) {
            if (item instanceof CredentialItem.InformationalMessage) {
                continue;
            }
            if (item instanceof CredentialItem.YesNoType) {
                ((CredentialItem.YesNoType) item).setValue(true);
            } else if (item instanceof CredentialItem.CharArrayType) {
                if (passphrase != null) {
                    ((CredentialItem.CharArrayType) item).setValue(passphrase.toCharArray());
                } else {
                    return false;
                }
            } else if (item instanceof CredentialItem.StringType) {
                if (passphrase != null) {
                    ((CredentialItem.StringType) item).setValue(passphrase);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }
}
