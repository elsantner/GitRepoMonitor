package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.authentication.Authenticator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;

public class GitManagerTestable extends GitManager {

  private boolean localChanges = false;
  private boolean remoteChanges = false;
  private GitAPIException throwOnFetchRepo;
  private RuntimeException throwOnFetchRepoRuntime;
  private IOException throwOnLocalChangesAvailable;

  GitManagerTestable() {
    // avoid super class constructor invocation
  }

  @Override
  protected void fetchRepo(Git repoGit, Authenticator authenticator) throws GitAPIException {
    if (throwOnFetchRepo != null) {
      throw throwOnFetchRepo;
    }
    if (throwOnFetchRepoRuntime != null) {
      throw throwOnFetchRepoRuntime;
    }
  }

  public void setLocalChanges(boolean localChanges) {
    this.localChanges = localChanges;
  }

  public void setRemoteChanges(boolean remoteChanges) {
    this.remoteChanges = remoteChanges;
  }

  @Override
  protected boolean localChangesAvailable(Git git) throws IOException, GitAPIException {
    if (throwOnLocalChangesAvailable != null) {
      throw throwOnLocalChangesAvailable;
    }
    return localChanges;
  }

  @Override
  protected boolean remoteChangesAvailable(Git git) throws IOException, GitAPIException, IllegalStateException {
    return remoteChanges;
  }

  public void throwOnFetchRepo(GitAPIException ex) {
    this.throwOnFetchRepo = ex;
  }

  public void throwOnFetchRepo(RuntimeException ex) {
    this.throwOnFetchRepoRuntime = ex;
  }

  public void throwOnLocalChangesAvailable(IOException ex) {
    this.throwOnLocalChangesAvailable = ex;
  }
}
