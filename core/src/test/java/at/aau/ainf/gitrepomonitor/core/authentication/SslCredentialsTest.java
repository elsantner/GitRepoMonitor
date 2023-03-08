package at.aau.ainf.gitrepomonitor.core.authentication;

import at.aau.ainf.gitrepomonitor.core.TestUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SslCredentialsTest extends AuthenticationCredentialsTest {

  static String GENERIC_PATH = "C:/somePath";
  static byte[] GENERIC_PASSPHRASE = "password".getBytes(StandardCharsets.UTF_8);

  @Test
  void testDestroy() {
    SslCredentials credentials = new SslCredentials(GENERIC_PATH, GENERIC_PASSPHRASE);
    credentials.destroy();
    assertTrue(TestUtils.isCleared(credentials.getSslPassphrase()));
  }

  @Override
  AuthenticationCredentials getCredentialsInstance() {
    return new SslCredentials(GENERIC_PATH, GENERIC_PASSPHRASE);
  }
}
