package at.aau.ainf.gitrepomonitor.core.authentication;

import at.aau.ainf.gitrepomonitor.core.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpsCredentialsTest extends AuthenticationCredentialsTest {

  static String GENERIC_USERNAME = "username";
  static char[] GENERIC_PASSWORD = "password".toCharArray();

  @Test
  void testDestroy() {
    HttpsCredentials credentials = new HttpsCredentials(GENERIC_USERNAME, GENERIC_PASSWORD);
    credentials.destroy();
    assertTrue(TestUtils.isCleared(credentials.getPassword()));
  }

  @Override
  AuthenticationCredentials getCredentialsInstance() {
    return new HttpsCredentials(GENERIC_USERNAME, GENERIC_PASSWORD);
  }
}
