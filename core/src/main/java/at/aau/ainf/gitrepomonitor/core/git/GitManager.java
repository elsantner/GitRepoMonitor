package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.MutableInteger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
    private ThreadPoolExecutor executor;

    private GitManager() {
        this.repoCache = new HashMap<>();
        this.fileManager = FileManager.getInstance();
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
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

    private MergeResult.MergeStatus pullRepo(String path, CredentialsProvider cp, ProgressMonitor progressMonitor) throws IOException, GitAPIException {
        Git git = getRepoGit(path);

        PullResult pullResult = git.pull()
                .setCredentialsProvider(cp)
                .setRemote("origin")
                .setProgressMonitor(progressMonitor)
                .call();
        if (pullResult.isSuccessful()) {
            updateRepoStatus(path);
        }
        System.out.println(pullResult.getMergeResult().getMergeStatus());
        return pullResult.getMergeResult().getMergeStatus();
    }

    public void pullRepoAsync(String path, PullCallback cb) {
        pullRepoAsync(path, null, cb, null);
    }

    public void pullRepoAsync(String path, PullCallback cb, ProgressMonitor progressMonitor) {
        pullRepoAsync(path, null, cb, progressMonitor);
    }

    public void pullRepoAsync(String path, String username, String password, PullCallback cb) {
        pullRepoAsync(path, new UsernamePasswordCredentialsProvider(username, password), cb, null);
    }

    public void pullRepoAsync(String path, String username, String password, PullCallback cb, ProgressMonitor progressMonitor) {
        pullRepoAsync(path, new UsernamePasswordCredentialsProvider(username, password), cb, progressMonitor);
    }

    private void pullRepoAsync(String path, CredentialsProvider cp, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(path, cp, progressMonitor);
                cb.finished(path, status.isSuccessful(), PullCallback.Status.values()[status.ordinal()], null);
            } catch (Exception e) {
                cb.finished(path, false, null, e);
            }
        });
    }

    public void updateWatchlistStatusAsync(UpdateStatusCallback cb) {
        List<RepositoryInformation> watchlist = fileManager.getWatchlist();
        MutableInteger checksFinished = new MutableInteger();
        checksFinished.value = 0;
        for (RepositoryInformation repo : watchlist) {
            updateRepoStatusAsync(repo.getPath(), (success, reposChecked, ex) -> {
                checksFinished.value++;
                // once all checks have finished, call callback
                if (checksFinished.value == watchlist.size()) {
                    cb.finished(true, checksFinished.value, ex);
                }
            });
        }
        if (watchlist.isEmpty()) {
            cb.finished(false, 0, null);
        }
    }

    public void updateRepoStatusAsync(String path, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(path);
                cb.finished(true, 1, null);
            } catch (Exception e) {
                e.printStackTrace();
                cb.finished(false, 1, e);
            }
        });
    }

    private void fetchRepo(String path) throws IOException, GitAPIException {
        Git git = getRepoGit(path);
        git.fetch().setRemote("origin").call();
    }

    public void pullWatchlistAsync(PullCallback cb, ProgressMonitor progessMonitor) {
        List<RepositoryInformation> pullableRepos = getPullableRepos(fileManager.getWatchlist());
        MutableInteger pullsFinished = new MutableInteger();
        pullsFinished.value = 0;
        List<PullCallback.PullResult> pullResults = new ArrayList<>();
        for (RepositoryInformation repo : pullableRepos) {
            pullRepoAsync(repo.getPath(), results -> {
                pullsFinished.value++;
                pullResults.addAll(results);
                // once all checks have finished, call callback
                if (pullsFinished.value == pullableRepos.size()) {
                    cb.finished(pullResults);
                }
            }, progessMonitor);
        }
        if (pullableRepos.isEmpty()) {
            cb.finished(new ArrayList<>());
        }
    }

    private List<RepositoryInformation> getPullableRepos(List<RepositoryInformation> list) {
        List<RepositoryInformation> pullableRepos = new ArrayList<>();
        for (RepositoryInformation repo : list) {
            if (repo.getStatus() == RepositoryInformation.RepoStatus.PULL_AVAILABLE) {
                pullableRepos.add(repo);
            }
        }
        return pullableRepos;
    }

    /**
     * Sets pullAvailable, pushAvailable, hasRemote and remoteAccessible of the Repo at the provided path.
     * @param path Path the repo is located at
     * @throws IOException If repo path is invalid
     * @throws GitAPIException If error during pull occurred
     */
    private void updateRepoStatus(String path) throws IOException, GitAPIException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        try {
            // update refs
            fetchRepo(path);
            Git git = getRepoGit(path);
            // query remote heads
            Map<String, Ref> refs = git.lsRemote()
                    .setHeads(true)
                    .setRemote("origin")
                    .callAsMap();
            // TODO: add support for multiple branches
            Ref remoteHead = refs.get(git.getRepository().getFullBranch());
            Ref localHead = git.getRepository().findRef("HEAD");


            // maybe usable for merging...
            /*ObjectReader reader = git.getRepository().newObjectReader();
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset( reader, getCommit(git.getRepository(), localHead.getObjectId()).getTree() );
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset( reader, getCommit(git.getRepository(), remoteHead.getObjectId()).getTree() );

            List<DiffEntry> diff = git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .call();
            */

            int commitTimeRemote = getCommit(git.getRepository(), remoteHead.getObjectId()).getCommitTime();
            int commitTimeLocal = getCommit(git.getRepository(), localHead.getObjectId()).getCommitTime();
            boolean equalHeads = remoteHead.getObjectId().equals(localHead.getObjectId());

            if (git.getRepository().readMergeHeads() != null) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.MERGE_NEEDED);
            } else if (!equalHeads && commitTimeRemote >= commitTimeLocal) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.PULL_AVAILABLE);
            } else if (!equalHeads) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.PUSH_AVAILABLE);
            } else {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.UP_TO_DATE);
            }
        }
        catch (NoRemoteRepositoryException | InvalidRemoteException ex) {
            repoInfo.setStatus(RepositoryInformation.RepoStatus.NO_REMOTE);
        }
        catch (TransportException ex) {
            if (ex.getCause() != null && ex.getCause() instanceof NoRemoteRepositoryException) {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.NO_REMOTE);
            } else {
                repoInfo.setStatus(RepositoryInformation.RepoStatus.INACCESSIBLE_REMOTE);
            }
        }
        fileManager.editRepo(repoInfo.getPath(), repoInfo);
    }

    private RevCommit getCommit(Repository repo, ObjectId objectId) throws IOException {
        RevCommit commit;
        try (RevWalk revWalk = new RevWalk(repo)) {
            commit = revWalk.parseCommit(objectId);
        }
        return commit;
    }
}
