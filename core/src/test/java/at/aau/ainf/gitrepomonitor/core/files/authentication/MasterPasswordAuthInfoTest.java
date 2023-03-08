package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.MasterPasswordAuthInfo;

public class MasterPasswordAuthInfoTest extends AuthenticationCredentialsTest {
  @Override
  AuthenticationCredentials getCredentialsInstance() {
    return new MasterPasswordAuthInfo();
  }
}
