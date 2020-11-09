package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.MutableInteger;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GitManager {
    private static GitManager instance;

    public static synchronized GitManager getInstance() {
        if (instance == null) {
            instance = new GitManager();
        }
        return instance;
    }

    private final HashMap<String, Git> repoCache;
    private FileManager fileManager;

    private GitManager() {
        this.repoCache = new HashMap<>();
        this.fileManager = FileManager.getInstance();
    }

    /**
     * Get a Repository object for the repository at the provided path
     * @param path Path of the folder wrapping the git repository
     * @return Repository at specified path
     * @throws IOException If path does not point to a valid repository
     */
    private synchronized Git getRepoGit(String path) throws IOException {
        Git repoGit = repoCache.get(path);
        if (repoGit == null) {
            Repository repo = new FileRepositoryBuilder()
                    .setGitDir(new File(path + "/.git"))
                    .build();
            repoGit = new Git(repo);
            repoCache.put(path, repoGit);
        }
        return repoGit;
    }

    private boolean pullRepo(String path, CredentialsProvider cp) throws IOException, GitAPIException {
        Git git = getRepoGit(path);

        PullResult pullResult = git.pull()
                .setCredentialsProvider(cp)
                .setRemote("origin")
                .call();
        if (pullResult.isSuccessful()) {
            updateRepoStatus(path);
        }

        return !pullResult.getFetchResult().getTrackingRefUpdates().isEmpty();
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
                boolean wasUpdated = pullRepo(path, cp);
                cb.finished(true, wasUpdated, null);
            } catch (Exception e) {
                cb.finished(false, false, e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public void updateWatchlistStatusAsync(UpdateStatusCallback cb) {
        List<RepositoryInformation> watchlist = fileManager.getWatchlist();
        MutableInteger checksFinished = new MutableInteger();
        checksFinished.value = 0;
        for (RepositoryInformation repo : watchlist) {
            updateRepoStatusAsync(repo.getPath(), (success, reposChecked, ex) -> {
                checksFinished.value++;
                if (checksFinished.value == watchlist.size()) {
                    cb.finished(true, checksFinished.value, ex);
                }
            });
        }
    }

    public void updateRepoStatusAsync(String path, UpdateStatusCallback cb) {
        Thread t = new Thread(() -> {
            try {
                updateRepoStatus(path);
                cb.finished(true, 1, null);
            } catch (Exception e) {
                e.printStackTrace();
                cb.finished(false, 1, e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void updateRepoStatus(String path) throws IOException, GitAPIException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        try {
            Git git = getRepoGit(path);
            Map<String, Ref> refs = git.lsRemote()
                    .setHeads(true)
                    .setRemote("origin")
                    .callAsMap();
            // TODO: add support for multiple branches
            Ref remoteHead = refs.get("refs/heads/main");
            Ref localHead = git.getRepository().findRef("HEAD");

            // repo is up to date if it has no remote or if update index is not negative
            repoInfo.setUpToDate(remoteHead == null || remoteHead.getObjectId().equals(localHead.getObjectId()));
        }
        catch (TransportException ex) {
            if (ex.getCause() != null && ex.getCause() instanceof NoRemoteRepositoryException) {
                repoInfo.setHasRemote(false);
            } else {
                repoInfo.setRemoteAccessible(false);
            }
        }
        fileManager.editRepo(repoInfo.getPath(), repoInfo);
    }
}
