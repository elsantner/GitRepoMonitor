package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureFileStorage;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.MutableInteger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation.RepoStatus.*;

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
    private SecureStorage secureStorage;
    private ThreadPoolExecutor executor;
    private PullListener pullListener;

    private GitManager() {
        this.repoCache = new HashMap<>();
        this.fileManager = FileManager.getInstance();
        this.secureStorage = SecureStorage.getImplementation();
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
    }

    public void setPullListener(PullListener pullListener) {
        this.pullListener = pullListener;
    }

    private void notifyPullListener(String path, MergeResult.MergeStatus status) {
        if (this.pullListener != null) {
            this.pullListener.pullExecuted(path, status);
        }
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

    private MergeResult.MergeStatus pullRepo(String path, CredentialsProvider cp, ProgressMonitor progressMonitor) throws IOException, InvalidConfigurationException {
        Git git = getRepoGit(path);

        try {
            PullResult pullResult = git.pull()
                    .setCredentialsProvider(cp)
                    .setRemote("origin")
                    .setProgressMonitor(progressMonitor)
                    .call();
            if (pullResult.isSuccessful()) {
                updateRepoStatus(path, cp);
            }
            // set new update count
            fileManager.setNewChanges(path, pullResult.getFetchResult().getTrackingRefUpdates().size());

            notifyPullListener(path, pullResult.getMergeResult().getMergeStatus());
            return pullResult.getMergeResult().getMergeStatus();
        } catch (InvalidConfigurationException ex) {
            throw ex;
        } catch (GitAPIException ex) {
            throw new SecurityException("authentication failed");
        }
    }

    private MergeResult.MergeStatus pullRepo(String path, char[] masterPW, ProgressMonitor progressMonitor) throws IOException, GitAPIException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        CredentialsProvider credProvider = null;
        if (repoInfo.isAuthenticated()) {
            credProvider = secureStorage.getHttpsCredentialProvider(masterPW, repoInfo.getID());
        }
        return pullRepo(path, credProvider, progressMonitor);
    }

    public void pullRepoAsync(String path, PullCallback cb) {
        pullRepoAsync(path, (char[]) null, cb, null);
    }

    public void pullRepoAsync(String path, PullCallback cb, ProgressMonitor progressMonitor) {
        pullRepoAsync(path, (char[]) null, cb, progressMonitor);
    }

    private void pullRepoAsync(String path, CredentialsProvider cp, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(path, cp, progressMonitor);
                cb.finished(path, status, null);
            } catch (SecurityException e) {
                cb.failed(true);
            } catch (InvalidConfigurationException e) {
                cb.failed(false);
            } catch (Exception e) {
                cb.finished(path, MergeResult.MergeStatus.FAILED, e);
            }
        });
    }

    public void pullRepoAsync(String path, char[] masterPW, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(path, masterPW, progressMonitor);
                cb.finished(path, status, null);
            } catch (Exception e) {
                cb.finished(path, MergeResult.MergeStatus.FAILED, e);
            }
        });
    }

    /**
     * Updates the status of all repositories on the Watchlist.
     * The master password is used to access stored credential information.
     * @param masterPW Master Password
     * @param cb Called when all repositories on the Watchlist have been checked.
     *           If the master password was incorrect, success = false and repos checked
     *           resembles the number of repos which could be checked without any credentials.
     */
    public void updateWatchlistStatusAsync(char[] masterPW, UpdateStatusCallback cb) {
        List<RepositoryInformation> watchlist = fileManager.getWatchlist();
        MutableInteger checksFinished = new MutableInteger();
        checksFinished.value = 0;
        MutableInteger checksSuccessful = new MutableInteger();
        checksSuccessful.value = 0;

        // load credentials of all repos if correct masterPW
        Map<UUID, CredentialsProvider> credentials = getCredentialsIfPossible(masterPW, watchlist);

        for (RepositoryInformation repo : watchlist) {
            updateRepoStatusAsync(repo.getPath(), credentials.get(repo.getID()),
                    (success, reposChecked, reposFailed, ex) -> {
                        checksFinished.value++;
                        if (success) checksSuccessful.value++;
                        // once all checks have finished, call callback
                        if (checksFinished.value == watchlist.size()) {
                            cb.finished(checksSuccessful.value == checksFinished.value,
                                    checksSuccessful.value,
                                    checksFinished.value - checksSuccessful.value,
                                    ex);
                        }
                    });
        }
        if (watchlist.isEmpty()) {
            cb.finished(true, 0, 0, null);
        }
    }

    public void updateWatchlistStatusAsync(UpdateStatusCallback cb) {
        updateWatchlistStatusAsync(null, cb);
    }

    public void updateRepoStatusAsync(String path, CredentialsProvider cp, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(path, cp);
                cb.finished(true, 1, 0, null);
            } catch (Exception e) {
                cb.finished(false, 0, 1, e);
            }
        });
    }

    public void updateRepoStatusAsync(String path, char[] masterPW, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(path, masterPW);
                cb.finished(true, 1, 0,null);
            } catch (Exception e) {
                cb.finished(false, 0, 1, e);
            }
        });
    }

    private void fetchRepo(Git repoGit, CredentialsProvider cp) throws GitAPIException {
        repoGit.fetch()
                .setRemote("origin")
                .setCredentialsProvider(cp)
                .call();
    }

    private Map<UUID, CredentialsProvider> getCredentialsIfPossible(char[] masterPW, List<RepositoryInformation> repos) {
        Map<UUID, CredentialsProvider> credentials = new HashMap<>();
        try {
            credentials = secureStorage.getHttpsCredentialProviders(masterPW, repos);
        } catch (Exception ex) {
            // nothing since repos without need for authentication will still be checked
        }
        return credentials;
    }

    public void pullWatchlistAsync(char[] masterPW, PullCallback cb, ProgressMonitor progressMonitor) {
        List<RepositoryInformation> watchlist = fileManager.getWatchlist();
        MutableInteger pullsFinished = new MutableInteger();
        pullsFinished.value = 0;
        MutableInteger pullsFailed = new MutableInteger();
        pullsFailed.value = 0;
        AtomicBoolean wrongMasterPW = new AtomicBoolean(false);

        // load credentials of all repos if correct masterPW
        Map<UUID, CredentialsProvider> credentials = getCredentialsIfPossible(masterPW, watchlist);

        List<PullCallback.PullResult> pullResults = new ArrayList<>();
        for (RepositoryInformation repo : watchlist) {

            pullRepoAsync(repo.getPath(), credentials.get(repo.getID()), (results, pullsFailedCount, wrongMP) -> {
                pullsFinished.value++;
                pullResults.addAll(results);
                pullsFailed.value += pullsFailedCount;
                wrongMasterPW.set(wrongMasterPW.get() || wrongMP);
                // once all pulls have finished, call callback
                if (pullsFinished.value == watchlist.size()) {
                    cb.finished(pullResults, pullsFailed.value, wrongMasterPW.get());
                }
            }, progressMonitor);
        }
        if (watchlist.isEmpty()) {
            cb.finished(new ArrayList<>(), 0, false);
        }
    }

    public void pullWatchlistAsync(PullCallback cb, ProgressMonitor progressMonitor) {
        pullWatchlistAsync(null, cb, progressMonitor);
    }
    /**
     * Sets status of the Repo at the provided path.
     * IF a master password != null is provided, the stored credentials of a repo are
     * attempted to be loaded and (if load is successful, i.e. master password is correct)
     * those credentials are used to access the repo.
     * @param path Path the repo is located at
     * @param masterPW Master Password for stored credentials
     * @throws IOException If repo path is invalid
     */
    private void updateRepoStatus(String path, char[] masterPW) throws IOException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        // if master password is provided & repo has authentication method specified, use those credentials
        try {
            CredentialsProvider cp = null;
            if (repoInfo.isAuthenticated()) {
                cp = secureStorage.getHttpsCredentialProvider(masterPW, repoInfo.getID());
            }
            updateRepoStatus(path, cp);
        } catch (SecurityException ex) {
            fileManager.updateRepoStatus(repoInfo.getPath(), WRONG_MASTER_PW);
            throw ex;
        }
    }

    private void updateRepoStatus(String path, CredentialsProvider cp) throws IOException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        RepositoryInformation.RepoStatus status = WRONG_MASTER_PW;
        try {
            if (repoInfo.isAuthenticated() && cp == null) {
                throw new SecurityException("wrong master password");
            } else {
                status = getRepoStatus(getRepoGit(path), cp);
            }
        } finally {
            fileManager.updateRepoStatus(repoInfo.getPath(), status);
        }
    }

    /**
     * Gets the current status of the given repository
     * @param repoGit Repository to check
     * @return Status of the repository
     */
    private RepositoryInformation.RepoStatus getRepoStatus(Git repoGit, CredentialsProvider cp) throws IOException {
        RepositoryInformation.RepoStatus status;

        try {
            // update refs
            fetchRepo(repoGit, cp);
            // query remote heads
            Map<String, Ref> refs = repoGit.lsRemote()
                    .setHeads(true)
                    .setRemote("origin")
                    .setCredentialsProvider(cp)
                    .callAsMap();
            // TODO: add support for multiple branches
            Ref remoteHead = refs.get(repoGit.getRepository().getFullBranch());
            Ref localHead = repoGit.getRepository().findRef("HEAD");

            int commitTimeRemote = getCommit(repoGit.getRepository(), remoteHead.getObjectId()).getCommitTime();
            int commitTimeLocal = getCommit(repoGit.getRepository(), localHead.getObjectId()).getCommitTime();
            boolean equalHeads = remoteHead.getObjectId().equals(localHead.getObjectId());

            if (repoGit.getRepository().readMergeHeads() != null) {
                status = MERGE_NEEDED;
            } else if (!equalHeads && commitTimeRemote >= commitTimeLocal) {
                status = PULL_AVAILABLE;
            } else if (!equalHeads) {
                status = PUSH_AVAILABLE;
            } else {
                status =UP_TO_DATE;
            }
        }
        catch (NoRemoteRepositoryException | InvalidRemoteException ex) {
            status = NO_REMOTE;
        }
        catch (TransportException ex) {
            if (ex.getCause() != null && ex.getCause() instanceof NoRemoteRepositoryException) {
                status = NO_REMOTE;
            } else {
                status = INACCESSIBLE_REMOTE;
            }
        } catch (GitAPIException ex) {
            status = UNKNOWN_ERROR;
        }

        return status;
    }

    private RevCommit getCommit(Repository repo, ObjectId objectId) throws IOException {
        RevCommit commit;
        try (RevWalk revWalk = new RevWalk(repo)) {
            commit = revWalk.parseCommit(objectId);
        }
        return commit;
    }

    /**
     * Asynchronously gets a list of all commits including changed files.
     * @param path Path of the repository.
     * @param cb Callback to be called when process finishes.
     */
    public void getLogAsync(String path, LogCallback cb) {
        executor.submit(() -> {
            try {
                cb.finished(true, getLog(path));
            } catch (Exception ex) {
                cb.finished(ex);
            }
        });
    }

    /**
     * Returns a list of all commits including changed files.
     * @param path Path of the repository.
     * @return List of all commits including changed files
     * @throws IOException If repository path is invalid
     * @throws GitAPIException If error during log generation occurs.
     */
    public List<CommitChange> getLog(String path) throws IOException, GitAPIException {
        Git git = getRepoGit(path);
        Iterable<RevCommit> log = git.log().call();
        List<CommitChange> changes = new ArrayList<>();

        // compare each commit to its immediate predecessor
        RevCommit prevRev = null;
        for (RevCommit rev : log) {
            if (prevRev != null) {
                List<DiffEntry> diffs = getDiff(git, rev.toObjectId(), prevRev.toObjectId());
                changes.add(new CommitChange(prevRev, diffs));
            }
            prevRev = rev;
        }
        // initial commit is compared to empty repository
        List<DiffEntry> diffs = getDiff(git, prevRev.toObjectId());
        changes.add(new CommitChange(prevRev, diffs));

        return changes;
    }

    /**
     * Returns a list of file changes between two commits.
     * @param git Git of which the commits are part of.
     * @param objectIdOld ID of the commit to which the new commit is compared.
     * @param objectIdNew ID of the commit which is compared to the old commit.
     * @return List of DiffEntries which represent file changes.
     * @throws IOException If invalid git is passed.
     * @throws GitAPIException If error during diff calculation occurs.
     */
    private List<DiffEntry> getDiff(Git git, ObjectId objectIdOld, ObjectId objectIdNew) throws IOException, GitAPIException {
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, getCommit(git.getRepository(), objectIdOld).getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, getCommit(git.getRepository(), objectIdNew).getTree());

            return git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .setShowNameAndStatusOnly(true)
                    .call();
        }
    }

    /**
     * Returns a list of file changes between the provided commit and the empty repository.
     * (This means all files in the commit will be marked as ADDED).
     * @param git Git of which the commits are part of.
     * @param objectIdInitial ID of the commit to which the new commit is compared.
     * @return List of DiffEntries which represent file changes.
     * @throws IOException If invalid git is passed.
     * @throws GitAPIException If error during diff calculation occurs.
     */
    private List<DiffEntry> getDiff(Git git, ObjectId objectIdInitial) throws IOException, GitAPIException {
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            EmptyTreeIterator oldTreeIter = new EmptyTreeIterator();
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, getCommit(git.getRepository(), objectIdInitial).getTree());

            return git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .setShowNameAndStatusOnly(true)
                    .call();
        }
    }

    public void testRepoConnectionAsync(RepositoryInformation repo, ConnectionTestCallback cb) {
        executor.submit(() -> cb.finished(testRepoConnection(repo, null)));
    }

    public void testRepoConnectionAsync(RepositoryInformation repo, String httpsUsername, String httpsPassword, ConnectionTestCallback cb) {
        executor.submit(() -> cb.finished(testRepoConnection(repo, new UsernamePasswordCredentialsProvider(httpsUsername, httpsPassword))));
    }

    private RepositoryInformation.RepoStatus testRepoConnection(RepositoryInformation repo, CredentialsProvider cp) {
        RepositoryInformation.RepoStatus status;
        try {
            Git git = getRepoGit(repo.getPath());
            status = getRepoStatus(git, cp);
        } catch (IOException e) {
            status = PATH_INVALID;
        }
        return status;
    }
}
