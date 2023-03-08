package at.aau.ainf.gitrepomonitor.core.authentication;

public class MasterPasswordAuthInfoTest extends AuthenticationCredentialsTest {
  @Override
  AuthenticationCredentials getCredentialsInstance() {
    return new MasterPasswordAuthInfo();
  }
}
