package at.aau.ainf.gitrepomonitor.core.git;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.core.files.Utils;
import at.aau.ainf.gitrepomonitor.core.files.authentication.AuthInfo;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.MutableInteger;

import javax.naming.AuthenticationException;
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

    public static void setAuthMethod(RepositoryInformation repoInfo) throws IOException {
        Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(repoInfo.getPath() + "/.git"))
                .build();

        String originURL = repo.getConfig().getString("remote", "origin", "url");
        if (originURL == null) {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.NONE);
        } else if (originURL.contains("https://")) {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.HTTPS);
        } else {
            repoInfo.setAuthMethod(RepositoryInformation.AuthMethod.SSL);
        }
    }

    private final HashMap<String, Git> repoCache;
    private final FileManager fileManager;
    private final ThreadPoolExecutor executor;
    private PullListener pullListener;

    private GitManager() {
        this.repoCache = new HashMap<>();
        this.fileManager = FileManager.getInstance();
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10, r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });
    }

    public void setPullListener(PullListener pullListener) {
        this.pullListener = pullListener;
    }

    public void pullRepoAsync(RepositoryInformation repo, char[] masterPW, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(repo, masterPW, progressMonitor);
                cb.finished(repo, status, null);
            } catch (Exception e) {
                handlePullException(e, cb, repo);
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
            updateRepoStatusAsync(repo, Optional.ofNullable(authInfo.get(repo.getID())).orElse(new AuthInfo()),
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

    public void updateRepoStatusAsync(RepositoryInformation repo, AuthInfo authInfo, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(repo, authInfo);
                cb.finished(true, 1, 0, null);
            } catch (Exception e) {
                cb.finished(false, 0, 1, e);
            } finally {
                authInfo.destroy();
            }
        });
    }

    public void updateRepoStatusAsync(RepositoryInformation repo, char[] masterPW, UpdateStatusCallback cb) {
        executor.submit(() -> {
            try {
                updateRepoStatus(repo, masterPW);
                cb.finished(true, 1, 0,null);
            } catch (Exception e) {
                e.printStackTrace();
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
            pullRepoAsync(repo, Optional.ofNullable(authInfo.get(repo.getID())).orElse(new AuthInfo()),
                    (results, pullsSuccessCount, pullsFailedCount, wrongMP) -> {
                synchronized (cb) {
                    pullsFinished.value++;
                    pullResults.addAll(results);
                    pullsSuccess.value += pullsSuccessCount;
                    pullsFailed.value += pullsFailedCount;
                    wrongMasterPW.set(wrongMasterPW.get() || wrongMP);
                    // once all pulls have finished, call callback
                    if (pullsFinished.value == watchlist.size()) {
                        cb.finished(pullResults, pullsSuccess.value, pullsFailed.value, wrongMasterPW.get());
                    }
                }
            }, progressMonitor);
        }
        if (watchlist.isEmpty()) {
            cb.finished(new ArrayList<>(), 0,0, false);
        }
    }

    /**
     * Asynchronously gets a list of all commits including changed files.
     * @param repo The repository.
     * @param cb Callback to be called when process finishes.
     */
    public void getLogAsync(RepositoryInformation repo, LogCallback cb) {
        executor.submit(() -> {
            try {
                cb.finished(true, getLog(repo));
            } catch (Exception ex) {
                cb.finished(ex);
            }
        });
    }

    /**
     * Returns a list of all commits including changed files.
     * @param repo The repository.
     * @return List of all commits including changed files
     * @throws IOException If repository path is invalid
     * @throws GitAPIException If error during log generation occurs.
     */
    public List<CommitChange> getLog(RepositoryInformation repo) throws IOException, GitAPIException {
        Git git = getRepoGit(repo.getPath());
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

    public void testRepoConnectionAsync(RepositoryInformation repo, AuthInfo authInfo, ConnectionTestCallback cb) {
        executor.submit(() -> {
            RepositoryInformation.RepoStatus testResult = testRepoConnection(repo, authInfo);
            authInfo.destroy();
            cb.finished(testResult);
        });
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

    public void checkout(RepositoryInformation repo, String branchName) throws IOException, GitAPIException {
        Git repoGit = getRepoGit(repo.getPath());
        repoGit.checkout()
                .setName(branchName)
                .call();
    }

    public void createBranch(RepositoryInformation repo, String branchName) throws IOException, GitAPIException {
        Git repoGit = getRepoGit(repo.getPath());
        repoGit.branchCreate()
                .setName(branchName)
                .call();
    }

    private void notifyPullListener(RepositoryInformation repo, MergeResult.MergeStatus status) {
        if (this.pullListener != null) {
            this.pullListener.pullExecuted(repo, status);
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

    private MergeResult.MergeStatus pullRepo(RepositoryInformation repo, AuthInfo authInfo, ProgressMonitor progressMonitor) throws IOException, CredentialException, CheckoutConflictException, WrongRepositoryStateException {
        Git git = getRepoGit(repo.getPath());
        RepositoryInformation repoInfo = fileManager.getRepo(repo.getID());

        try {
            ObjectId oldHead = git.getRepository().resolve("HEAD");
            PullCommand cmd = git.pull()
                    .setStrategy(repoInfo.getMergeStrategy().getJgitStrat())
                    .setProgressMonitor(progressMonitor);
            authInfo.configure(cmd);
            PullResult pullResult = cmd.call();
            ObjectId head = git.getRepository().resolve("HEAD");

            // set new update count
            fileManager.setNewChanges(repo.getID(), getCommitsInRange(git, oldHead, head).size());

            notifyPullListener(repo, pullResult.getMergeResult().getMergeStatus());
            return pullResult.getMergeResult().getMergeStatus();

        } catch (WrongRepositoryStateException | CheckoutConflictException ex) {
            throw ex;
        } catch (InvalidConfigurationException ex) {
            throw new NoRemoteRepositoryException(new URIish(), "no remote");
        } catch (TransportException ex) {
            throw new CredentialException("invalid https credentials");
        } catch (GitAPIException ex) {
            throw new SecurityException("authentication failed");
        } finally {
            updateRepoStatus(repo, authInfo);
        }
    }

    private MergeResult.MergeStatus pullRepo(RepositoryInformation repo, char[] masterPW, ProgressMonitor progressMonitor) throws IOException, GitAPIException, CredentialException, AuthenticationException {
        RepositoryInformation repoInfo = fileManager.getRepo(repo.getID());
        AuthInfo authInfo = AuthInfo.getFor(repoInfo, masterPW);
        MergeResult.MergeStatus status = pullRepo(repo, authInfo, progressMonitor);
        authInfo.destroy();
        return status;
    }

    private void pullRepoAsync(RepositoryInformation repo, AuthInfo authInfo, PullCallback cb, ProgressMonitor progressMonitor) {
        executor.submit(() -> {
            MergeResult.MergeStatus status;
            try {
                status = pullRepo(repo, authInfo, progressMonitor);
                cb.finished(repo, status, null);
            } catch (Exception e) {
                handlePullException(e, cb, repo);
            }
        });
    }

    private void handlePullException(Exception ex, PullCallback cb, RepositoryInformation repo) {
        // wrong master password
        if (ex instanceof SecurityException) {
            cb.failed(repo, MergeResult.MergeStatus.FAILED, ex, true);
        // wrong credentials or no remote
        } else if (ex instanceof CredentialException || ex instanceof  NoRemoteRepositoryException) {
            cb.failed(repo, MergeResult.MergeStatus.FAILED, ex,false);
        // checkout failed
        } else if (ex instanceof CheckoutConflictException) {
            cb.failed(repo, MergeResult.MergeStatus.CHECKOUT_CONFLICT, ex, false);
        // repository is in a bad state (e.g. merging)
        } else if (ex instanceof WrongRepositoryStateException) {
            cb.finished(repo, MergeResult.MergeStatus.CONFLICTING, ex);
        // other error
        } else {
            cb.finished(repo, MergeResult.MergeStatus.FAILED, ex);
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
     * @param repo The Repository
     * @param masterPW Master Password for stored credentials
     * @throws IOException If repo path is invalid
     */
    private void updateRepoStatus(RepositoryInformation repo, char[] masterPW) throws IOException, AuthenticationException {
        RepositoryInformation repoInfo = fileManager.getRepo(repo.getID());
        // if master password is provided & repo has authentication method specified, use those credentials
        AuthInfo authInfo = null;
        try {
            authInfo = AuthInfo.getFor(repoInfo, masterPW);
            updateRepoStatus(repo, authInfo);
        } catch (SecurityException | AuthenticationException ex) {
            fileManager.updateRepoStatus(repoInfo.getID(), WRONG_MASTER_PW);
            throw ex;
        } finally {
            if (authInfo != null)
                authInfo.destroy();
        }
    }

    private void updateRepoStatus(RepositoryInformation repo, AuthInfo authInfo) throws IOException {
        RepositoryInformation repoInfo = fileManager.getRepo(repo.getID());
        RepositoryInformation.RepoStatus status = WRONG_MASTER_PW;
        try {
            if (!Utils.validateRepositoryPath(repoInfo.getPath())) {
                status = PATH_INVALID;
            } else if (repoInfo.getAuthID() != null && !authInfo.hasInformation()) {
                throw new SecurityException("wrong master password");
            } else {
                status = getRepoStatus(getRepoGit(repo.getPath()), authInfo);
            }
        } finally {
            fileManager.updateRepoStatus(repoInfo.getID(), status);
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
            boolean pullAvailable = remoteChangesAvailable(repoGit);
            boolean pushAvailable = localChangesAvailable(repoGit);

            if (repoGit.getRepository().readMergeHeads() != null) {
                status = MERGE_NEEDED;
            } else if (pullAvailable && pushAvailable) {
                status = PULL_PUSH_AVAILABLE;
            } else if (pullAvailable) {
                status = PULL_AVAILABLE;
            } else if (pushAvailable) {
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

    /**
     * Check if local changes are available to pull.
     * @param git Git of repository
     * @return True, if local changes are available.
     * @throws IOException
     * @throws GitAPIException
     */
    private boolean localChangesAvailable(Git git) throws IOException, GitAPIException {
        Repository repository = git.getRepository();
        String branch = repository.getBranch();
        ObjectId fetchHead = repository.resolve("refs/remotes/origin/"+branch);
        ObjectId head = repository.resolve("refs/heads/"+branch);
        // check if there are any commits in local tree which are not in remote tree
        return !getExclusiveCommits(git.log().add(fetchHead).call(), git.log().add(head).call()).isEmpty();
    }

    /**
     * Check if remote changes are available to pull.
     * @param git Git of repository
     * @return True, if remote changes are available.
     * @throws IOException
     * @throws GitAPIException
     */
    private boolean remoteChangesAvailable(Git git) throws IOException, GitAPIException {
        Repository repository = git.getRepository();
        String branch = repository.getBranch();
        ObjectId fetchHead = repository.resolve("refs/remotes/origin/"+branch);
        ObjectId head = repository.resolve("refs/heads/"+branch);
        // check if there are any commits in remote tree which are not in local tree
        return !getExclusiveCommits(git.log().add(head).call(), git.log().add(fetchHead).call()).isEmpty();
    }

    /**
     * Compare 'comparable' to 'base' and get the commits exclusive to 'comparable' (i.e. which do not exists in 'base')
     * @param base
     * @param comparable
     * @return
     */
    private List<RevCommit> getExclusiveCommits(Iterable<RevCommit> base, Iterable<RevCommit> comparable) {
        List<RevCommit> exclusiveCommits = new ArrayList<>();
        Set<RevCommit> baseCommits = new HashSet<>();
        base.forEach(baseCommits::add);
        Set<RevCommit> comparableCommits = new HashSet<>();
        comparable.forEach(comparableCommits::add);

        for (RevCommit rc : comparableCommits) {
            if (!baseCommits.contains(rc)) {
                exclusiveCommits.add(rc);
            }
        }
        return exclusiveCommits;
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

    public boolean hasRemoteRepository(String path) {
        return getRemoteURL(path) != null;
    }

    public String getRemoteURL(String path) {
        try {
            Repository repo = getRepoGit(path).getRepository();
            return repo.getConfig().getString("remote", "origin", "url");
        } catch (IOException e) {
            return null;
        }
    }
}
