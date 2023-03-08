package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.authentication.AuthenticationCredentials;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class AuthenticationCredentialsTest {

  @Test
  void testSerialize() throws JsonProcessingException {
    XmlMapper mapper = XmlMapper.xmlBuilder().build();
    AuthenticationCredentials credentials = getCredentialsInstance();
    String xmlString = mapper.writeValueAsString(credentials);

    AuthenticationCredentials deserializedCredentials = mapper.readValue(xmlString, new TypeReference<>() {});
    assertEquals(credentials.getClass(), deserializedCredentials.getClass());
  }

  abstract AuthenticationCredentials getCredentialsInstance();

  @Test
  void testGenerateID() {
    AuthenticationCredentials authenticationCredentials = new AuthenticationCredentialsTestable();
    assertNotNull(authenticationCredentials.getID());
  }

  static class AuthenticationCredentialsTestable extends AuthenticationCredentials {

    @Override
    public RepositoryInformation.AuthMethod getAuthMethod() {
      return null;
    }

    @Override
    public void destroy() {

    }
  }
}
