package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.TestUtils;
import at.aau.ainf.gitrepomonitor.core.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.HttpsCredentials;
import at.aau.ainf.gitrepomonitor.core.authentication.SslCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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
