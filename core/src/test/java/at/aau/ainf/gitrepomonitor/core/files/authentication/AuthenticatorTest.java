package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.authentication.*;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.TransportCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.naming.AuthenticationException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthenticatorTest {

  static UUID authIdHTTPS = UUID.randomUUID();
  static UUID authIdSSL = UUID.randomUUID();

  static SecureStorage secureStorageMock;
  static char[] SOME_MP = "mp".toCharArray();

  @BeforeAll
  static void setupMock() throws AuthenticationException {
    secureStorageMock = mock(SecureStorage.class);
    doAnswer(invocation -> {
      UUID id = invocation.getArgument(1);
      if (authIdHTTPS.equals(id)) {
        return new HttpsCredentials();
      }
      else if (authIdSSL.equals(id)) {
        return new SslCredentials();
      }
      return null;
    })
        .when(secureStorageMock)
        .get(any(), (UUID) any());

    when(secureStorageMock.get(any(), (Collection<UUID>) any())).then(invocation -> {
      Map<UUID, AuthenticationCredentials> authCredentials = new HashMap<>();
      authCredentials.put(authIdHTTPS, new HttpsCredentials());
      authCredentials.put(authIdSSL, new SslCredentials());
      return authCredentials;
    });
  }

  @Test
  void testHttps() throws AuthenticationException {
    Authenticator authenticator = Authenticator.get(authIdHTTPS, SOME_MP, secureStorageMock);
    assertTrue(authenticator.hasInformation());

    TransportCommand cmd = mock(TransportCommand.class);
    authenticator.configure(cmd);
    verify(cmd, times(1)).setCredentialsProvider(any());
  }

  @Test
  void testSsl() throws AuthenticationException {
    Authenticator authenticator = Authenticator.get(authIdSSL, SOME_MP, secureStorageMock);
    assertTrue(authenticator.hasInformation());

    TransportCommand cmd = mock(TransportCommand.class);
    authenticator.configure(cmd);
    verify(cmd, times(1)).setTransportConfigCallback(any());
  }

  @Test
  void testNonexistent() throws AuthenticationException {
    Authenticator authenticator = Authenticator.get(UUID.randomUUID(), SOME_MP, secureStorageMock);
    assertFalse(authenticator.hasInformation());
  }

  @Test
  void testGetFor() throws AuthenticationException {
    List<RepositoryInformation> repos = new ArrayList<>();
    repos.add(createRepoWithAuthId(authIdHTTPS));
    repos.add(createRepoWithAuthId(authIdHTTPS));
    repos.add(createRepoWithAuthId(authIdSSL));
    repos.add(createRepoWithAuthId(null));

    Map<UUID, Authenticator> authenticators = Authenticator.getFor(repos, SOME_MP, secureStorageMock);

    assertEquals(4, authenticators.size());
    for (RepositoryInformation repo : repos) {
      assertNotNull(authenticators.get(repo.getID()));
    }
    // different authenticator instances for different repos with same authID
    assertNotEquals(authenticators.get(repos.get(0).getID()), authenticators.get(repos.get(1).getID()));

    // verify authenticator contents
    assertTrue(authenticators.get(repos.get(0).getID()).hasInformation());
    assertTrue(authenticators.get(repos.get(1).getID()).hasInformation());
    assertTrue(authenticators.get(repos.get(2).getID()).hasInformation());
    assertFalse(authenticators.get(repos.get(3).getID()).hasInformation());
  }

  private RepositoryInformation createRepoWithAuthId(UUID authId) {
    return new RepositoryInformation(UUID.randomUUID(), "", "",
        RepositoryInformation.MergeStrategy.RECURSIVE, authId);
  }

}
