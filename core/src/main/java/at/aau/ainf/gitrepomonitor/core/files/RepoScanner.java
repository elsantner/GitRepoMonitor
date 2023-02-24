package at.aau.ainf.gitrepomonitor.core.files;

import at.aau.ainf.gitrepomonitor.core.files.RepoScanCallback;
import at.aau.ainf.gitrepomonitor.core.git.GitManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Scanner for Git repositories on current PC.
 */
public class RepoScanner {

    private File rootDir;
    private boolean excludeNoRemote;
    private boolean isStopped;
    private GitManager gitManager;

    public RepoScanner(File rootDir, boolean excludeNoRemote) {
        this.rootDir = rootDir;
        this.excludeNoRemote = excludeNoRemote;
        this.isStopped = true;
        this.gitManager = GitManager.getInstance();
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void stop() {
        isStopped = true;
    }

    /**
     * Scan PC. If a root dir (!= null) is specified, scan all directories below.
     * If no root is specified, scan all drives on the PC.
     * @param cb Callback
     * @return List of all found repos
     */
    public List<File> scanForRepos(RepoScanCallback cb) {
        isStopped = false;
        List<File> repos = new ArrayList<>();
        if (rootDir != null) {
            scanForReposRecursive(rootDir, repos, cb);
        }
        else {
            for (File driveRoot: File.listRoots()) {
                scanForReposRecursive(driveRoot, repos, cb);
            }
        }
        isStopped = true;
        return repos;
    }

    /**
     * Scan all directories below {@code rootDir}
     * @param rootDir Root
     * @param repos Current repo list
     * @param cb Callback
     */
    private void scanForReposRecursive(File rootDir, List<File> repos, RepoScanCallback cb) {
        if (!isStopped) {
            // if repo found
            if (rootDir.getName().equals(".git")) {
                // when excludeNoRemote is true, only add repo to found list if it has a valid remote
                if (!excludeNoRemote || gitManager.hasRemoteRepository(rootDir.getParentFile().getAbsolutePath())) {
                    repos.add(rootDir.getParentFile());     // add repo folder to list
                    cb.repoFound(rootDir.getParentFile());
                }
            } else {
                cb.dirScanned();
                File[] childDirs = rootDir.listFiles(File::isDirectory);
                if (childDirs != null) {
                    for (File childDir : childDirs)
                        scanForReposRecursive(childDir, repos, cb);
                }
            }
        }
    }
}
