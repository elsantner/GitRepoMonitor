package at.aau.ainf.gitrepomonitor.core.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import java.util.UUID;

/**
 * Special auth credentials used to check master password validity.
 */
public class MasterPasswordAuthInfo extends AuthenticationCredentials {

    public static UUID ID = new UUID(0, 0);

    public MasterPasswordAuthInfo() {
        this.id = ID;
        this.name = "MP_SET";
    }

    @Override
    public RepositoryInformation.AuthMethod getAuthMethod() {
        return RepositoryInformation.AuthMethod.NONE;
    }

    @Override
    public void destroy() {
        // nothing sensitive in here
    }
}
