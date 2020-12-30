package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthInfo;
import at.aau.ainf.gitrepomonitor.core.files.authentication.SecureStorage;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.MutableInteger;

import javax.security.auth.login.CredentialException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation.RepoStatus.*;

public class GitManager {
    private static GitManager instance;
    private static final String PATTERN_HEAD_COMMIT = ".*HEAD$";

    public static synchronized GitManager getInstance() {
        if (instance == null) {
            instance = new GitManager();
        }
        return instance;
    }

    private final HashMap<String, Git> repoCache;
    private final FileManager fileManager;
    private final SecureStorage secureStorage;
    private final ThreadPoolExecutor executor;
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

    public void pullRepoAsync(String path, char[] masterPW, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(path, masterPW, progressMonitor);
                cb.finished(path, status, null);
            } catch (Exception e) {
                handlePullException(e, cb, path);
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
        Map<UUID, AuthInfo> authInfo = getAuthInfoIfPossible(masterPW, watchlist);

        for (RepositoryInformation repo : watchlist) {
            updateRepoStatusAsync(repo.getPath(), Optional.ofNullable(authInfo.get(repo.getID())).orElse(new AuthInfo()),
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

    public void updateRepoStatusAsync(String path, AuthInfo authInfo, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(path, authInfo);
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

    public void pullWatchlistAsync(char[] masterPW, PullCallback cb, ProgressMonitor progressMonitor) {
        List<RepositoryInformation> watchlist = fileManager.getWatchlist();
        MutableInteger pullsFinished = new MutableInteger();
        pullsFinished.value = 0;
        MutableInteger pullsSuccess = new MutableInteger();
        pullsSuccess.value = 0;
        MutableInteger pullsFailed = new MutableInteger();
        pullsFailed.value = 0;
        AtomicBoolean wrongMasterPW = new AtomicBoolean(false);

        // load credentials of all repos if correct masterPW
        Map<UUID, AuthInfo> authInfo = getAuthInfoIfPossible(masterPW, watchlist);

        List<PullCallback.PullResult> pullResults = new ArrayList<>();
        for (RepositoryInformation repo : watchlist) {
            pullRepoAsync(repo.getPath(), Optional.ofNullable(authInfo.get(repo.getID())).orElse(new AuthInfo()),
                    (results, pullsSuccessCount, pullsFailedCount, wrongMP) -> {
                pullsFinished.value++;
                pullResults.addAll(results);
                pullsSuccess.value += pullsSuccessCount;
                pullsFailed.value += pullsFailedCount;
                wrongMasterPW.set(wrongMasterPW.get() || wrongMP);
                // once all pulls have finished, call callback
                if (pullsFinished.value == watchlist.size()) {
                    cb.finished(pullResults, pullsSuccess.value, pullsFailed.value, wrongMasterPW.get());
                }
            }, progressMonitor);
        }
        if (watchlist.isEmpty()) {
            cb.finished(new ArrayList<>(), 0,0, false);
        }
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

    public void testRepoConnectionAsync(RepositoryInformation repo, ConnectionTestCallback cb) {
        executor.submit(() -> cb.finished(testRepoConnection(repo, new AuthInfo())));
    }

    public void testRepoConnectionHttpsAsync(RepositoryInformation repo, String httpsUsername, String httpsPassword,
                                        ConnectionTestCallback cb) {
        executor.submit(() -> cb.finished(testRepoConnection(repo,
                new AuthInfo(new UsernamePasswordCredentialsProvider(httpsUsername, httpsPassword)))));
    }

    public void testRepoConnectionSslAsync(RepositoryInformation repo, String sslKeyPath, String sslPassphrase,
                                        ConnectionTestCallback cb) {
        executor.submit(() -> cb.finished(testRepoConnection(repo,
                new AuthInfo(new SSLTransportConfigCallback(sslKeyPath, sslPassphrase)))));
    }

    public Collection<Branch> getBranchNames(String path) throws IOException, GitAPIException {
        Map<String, Branch> branches = new HashMap<>();
        Git repoGit = getRepoGit(path);
        // add all local branches to list
        List<Ref> localBranches = repoGit
                .branchList()
                .call();
        for (Ref b : localBranches) {
            Branch branch = new Branch(b.getName(), false);
            branches.put(branch.getShortName(), branch);
        }

        // add all remote branches which are not also local
        List<Ref> remoteBranches = repoGit
                .branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call();
        for (Ref b : remoteBranches) {
            if (!Pattern.matches(PATTERN_HEAD_COMMIT, b.getName())) {
                Branch branch = new Branch(b.getName(), true);
                if (!branches.containsKey(branch.getShortName())) {
                    branches.put(branch.getShortName(), branch);
                }
            }
        }

        return branches.values();
    }

    public Branch getSelectedBranch(String path) throws IOException {
        Git repoGit = getRepoGit(path);
        // selected branch is always local
        return new Branch("refs/heads/" + repoGit.getRepository().getBranch(), false);
    }

    public void checkout(String path, String branchName) throws IOException, GitAPIException {
        Git repoGit = getRepoGit(path);
        repoGit.checkout()
                .setName(branchName)
                .call();
    }

    public void createBranch(String path, String branchName) throws IOException, GitAPIException {
        Git repoGit = getRepoGit(path);
        repoGit.branchCreate()
                .setName(branchName)
                .call();
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

    private List<RevCommit> getCommitsInRange(Git git, ObjectId from, ObjectId to) {
        List<RevCommit> commits = new ArrayList<>();
        try {
            git.log().addRange(from, to).call().forEach(commits::add);
        } catch(Exception ex) {
            return new ArrayList<>();
        }
        return commits;
    }

    private MergeResult.MergeStatus pullRepo(String path, AuthInfo authInfo, ProgressMonitor progressMonitor) throws IOException, CredentialException, CheckoutConflictException, WrongRepositoryStateException {
        Git git = getRepoGit(path);
        RepositoryInformation repoInfo = fileManager.getRepo(path);

        try {
            ObjectId oldHead = git.getRepository().resolve("HEAD");
            PullCommand cmd = git.pull()
                    .setStrategy(repoInfo.getMergeStrategy().getJgitStrat())
                    .setProgressMonitor(progressMonitor);
            authInfo.configure(cmd);
            PullResult pullResult = cmd.call();
            ObjectId head = git.getRepository().resolve("HEAD");

            // set new update count
            fileManager.setNewChanges(path, getCommitsInRange(git, oldHead, head).size());

            notifyPullListener(path, pullResult.getMergeResult().getMergeStatus());
            return pullResult.getMergeResult().getMergeStatus();

        } catch (WrongRepositoryStateException ex) {
            throw ex;
        } catch (InvalidConfigurationException ex) {
            throw new NoRemoteRepositoryException(new URIish(), "no remote");
        } catch (TransportException ex) {
            throw new CredentialException("invalid https credentials");
        } catch (CheckoutConflictException ex) {
            throw ex;
        } catch (GitAPIException ex) {
            throw new SecurityException("authentication failed");
        } finally {
            updateRepoStatus(path, authInfo);
        }
    }

    private MergeResult.MergeStatus pullRepo(String path, char[] masterPW, ProgressMonitor progressMonitor) throws IOException, GitAPIException, CredentialException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        AuthInfo authInfo = AuthInfo.getFor(repoInfo, masterPW);
        MergeResult.MergeStatus status = pullRepo(path, authInfo, progressMonitor);
        authInfo.destroy();
        return status;
    }

    private void pullRepoAsync(String path, AuthInfo authInfo, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(path, authInfo, progressMonitor);
                cb.finished(path, status, null);
            } catch (Exception e) {
                handlePullException(e, cb, path);
            }
        });
    }

    private void handlePullException(Exception ex, PullCallback cb, String path) {
        if (ex instanceof SecurityException) {
            cb.failed(path, true);
        } else if (ex instanceof CredentialException || ex instanceof  NoRemoteRepositoryException) {
            cb.failed(path, false);
        } else if (ex instanceof CheckoutConflictException) {
            cb.failed(path, MergeResult.MergeStatus.CHECKOUT_CONFLICT, ex, false);
        } else if (ex instanceof WrongRepositoryStateException) {
            cb.finished(path, MergeResult.MergeStatus.CONFLICTING, ex);
        } else {
            cb.finished(path, MergeResult.MergeStatus.FAILED, ex);
        }
    }

    private void fetchRepo(Git repoGit, AuthInfo authInfo) throws GitAPIException {
        FetchCommand cmd = repoGit.fetch();
        authInfo.configure(cmd);
        cmd.call();
    }


    private Map<UUID, AuthInfo> getAuthInfoIfPossible(char[] masterPW, List<RepositoryInformation> repos) {
        Map<UUID, AuthInfo> authInfo = new HashMap<>();
        try {
            authInfo = AuthInfo.getFor(repos, masterPW);
        } catch (Exception ex) {
            // nothing since repos without need for authentication will still be checked
        }
        return authInfo;
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
            AuthInfo authInfo = AuthInfo.getFor(repoInfo, masterPW);
            updateRepoStatus(path, authInfo);
        } catch (SecurityException ex) {
            fileManager.updateRepoStatus(repoInfo.getPath(), WRONG_MASTER_PW);
            throw ex;
        }
    }

    private void updateRepoStatus(String path, AuthInfo authInfo) throws IOException {
        RepositoryInformation repoInfo = fileManager.getRepo(path);
        RepositoryInformation.RepoStatus status = WRONG_MASTER_PW;
        try {
            if (repoInfo.isAuthenticated() && !authInfo.hasInformation()) {
                throw new SecurityException("wrong master password");
            } else {
                status = getRepoStatus(getRepoGit(path), authInfo);
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
    private RepositoryInformation.RepoStatus getRepoStatus(Git repoGit, AuthInfo authInfo) throws IOException {
        RepositoryInformation.RepoStatus status;

        try {
            // update refs
            fetchRepo(repoGit, authInfo);
            // query remote heads
            LsRemoteCommand cmd = repoGit.lsRemote().setHeads(true);
            authInfo.configure(cmd);
            Map<String, Ref> refs = cmd.callAsMap();

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
                status = UP_TO_DATE;
            }
        }
        catch (NoRemoteRepositoryException | InvalidRemoteException ex) {
            status = NO_REMOTE;
        }
        catch (TransportException ex) {
            ex.printStackTrace();
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

    private RepositoryInformation.RepoStatus testRepoConnection(RepositoryInformation repo, AuthInfo authInfo) {
        RepositoryInformation.RepoStatus status;
        try {
            Git git = getRepoGit(repo.getPath());
            status = getRepoStatus(git, authInfo);
        } catch (IOException e) {
            status = PATH_INVALID;
        }
        return status;
    }
}
