package at.aau.ainf.gitrepomonitor.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class GitManager {
    private static GitManager instance;

    public static synchronized GitManager getInstance() {
        if (instance == null) {
            instance = new GitManager();
        }
        return instance;
    }

    private GitManager() {
    }

    private boolean pullRepo(String path, CredentialsProvider cp) throws IOException, GitAPIException {
        Repository existingRepo = new FileRepositoryBuilder()
                .setGitDir(new File(path + "/.git"))
                .build();
        Git git = new Git(existingRepo);

        PullResult pullResult = git.pull()
                .setCredentialsProvider(cp)
                .setRemote("origin")
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .call();
        return pullResult.isSuccessful();
    }

    public boolean pullRepo(String path, String username, String password) throws IOException, GitAPIException {
        return pullRepo(path, new UsernamePasswordCredentialsProvider(username, password));
    }

    public boolean pullRepo(String path) throws IOException, GitAPIException {
        return pullRepo(path, null);
    }

    public void pullRepoAsync(String path, PullCallback cb) {
        pullRepoAsync(path, null, cb);
    }

    public void pullRepoAsync(String path, String username, String password, PullCallback cb) {
        pullRepoAsync(path, new UsernamePasswordCredentialsProvider(username, password), cb);
    }

    private void pullRepoAsync(String path, CredentialsProvider cp, PullCallback cb) {
        Thread t = new Thread(() -> {
            try {
                pullRepo(path, cp);
                cb.finished(true, null);
            } catch (Exception e) {
                cb.finished(false, e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

}
