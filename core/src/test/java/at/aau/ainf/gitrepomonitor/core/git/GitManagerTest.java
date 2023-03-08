package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.authentication.Authenticator;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitManagerTest {

  Git getGitMock(boolean mergeNeeded) throws IOException {
    Repository repoMock = mock(Repository.class);
    when(repoMock.readMergeHeads()).thenReturn(mergeNeeded ? new ArrayList<>() : null);

    Git gitMock = mock(Git.class);
    when(gitMock.lsRemote()).thenReturn(mock(LsRemoteCommand.class));
    when(gitMock.getRepository()).thenReturn(repoMock);
    return gitMock;
  }

  Git getGitMock() throws IOException {
    return getGitMock(false);
  }

  @Test
  void testGetStatus_UpToDate() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.UP_TO_DATE, status);
  }

  @Test
  void testGetStatus_PullAvailable() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.setRemoteChanges(true);
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.PULL_AVAILABLE, status);
  }

  @Test
  void testGetStatus_PushAvailable() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.setLocalChanges(true);
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.PUSH_AVAILABLE, status);
  }

  @Test
  void testGetStatus_PullPushAvailable() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.setRemoteChanges(true);
    gitManager.setLocalChanges(true);
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.PULL_PUSH_AVAILABLE, status);
  }

  @Test
  void testGetStatus_NoRemoteBranch() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnFetchRepo(new IllegalStateException());
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.NO_REMOTE_BRANCH, status);
  }

  @Test
  void testGetStatus_NoRemote1() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnLocalChangesAvailable(mock(NoRemoteRepositoryException.class));
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.NO_REMOTE, status);
  }

  @Test
  void testGetStatus_NoRemote2() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnFetchRepo(mock(InvalidRemoteException.class));
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.NO_REMOTE, status);
  }

  @Test
  void testGetStatus_NoRemote3() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnFetchRepo(
        new TransportException("",
            new NoRemoteRepositoryException(mock(URIish.class), "")));

    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.NO_REMOTE, status);
  }

  @Test
  void testGetStatus_InaccessibleRemote() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnFetchRepo(
        new TransportException(""));

    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.INACCESSIBLE_REMOTE, status);
  }

  @Test
  void testGetStatus_Unknown() throws IOException {
    GitManagerTestable gitManager = new GitManagerTestable();
    gitManager.throwOnFetchRepo(mock(GitAPIException.class));
    RepositoryInformation.RepoStatus status = gitManager.getRepoStatus(getGitMock(true), new Authenticator());
    assertEquals(RepositoryInformation.RepoStatus.UNKNOWN_ERROR, status);
  }
}
