package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;

import java.util.UUID;

public class MasterPasswordAuthInfo extends AuthenticationInformation {

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
