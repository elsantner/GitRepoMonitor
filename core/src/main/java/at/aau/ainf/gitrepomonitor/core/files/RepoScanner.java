package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepoScanner {

    private File rootDir;
    private boolean isStopped;

    public RepoScanner(File rootDir) {
        this.rootDir = rootDir;
        this.isStopped = true;
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

    private void scanForReposRecursive(File rootDir, List<File> repos, RepoScanCallback cb) {
        if (!isStopped) {
            if (rootDir.getName().equals(".git")) {
                repos.add(rootDir.getParentFile());     // add repo folder to list
                cb.repoFound(rootDir.getParentFile());
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
