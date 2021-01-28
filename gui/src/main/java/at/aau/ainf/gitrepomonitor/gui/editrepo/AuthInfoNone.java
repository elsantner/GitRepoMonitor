package at.aau.ainf.gitrepomonitor.gui.editrepo;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthenticationInformation;

import java.util.UUID;

/**
 * Exists in order to add an arbitrary AuthenticationInformation to Lists / ComboBoxes
 */
public class AuthInfoNone extends AuthenticationInformation {

    public AuthInfoNone(String name) {
        super(new UUID(0,0), name);
    }

    @Override
    public RepositoryInformation.AuthMethod getAuthMethod() {
        return RepositoryInformation.AuthMethod.NONE;
    }

    @Override
    public void destroy() {
        // nothing
    }
}
