package at.aau.ainf.gitrepomonitor.files;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RepoScanner {

    private File rootDir;
    private boolean isStopped;

    public RepoScanner(File rootDir) {
        this.rootDir = rootDir;
        this.isStopped = false;
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
        List<File> repos = new ArrayList<>();
        if (rootDir != null) {
            scanForReposRecursive(rootDir, repos, cb);
        }
        else {
            for (File driveRoot: File.listRoots()) {
                scanForReposRecursive(driveRoot, repos, cb);
            }
        }
        return repos;
    }

    // TODO: make faster!
    private void scanForReposRecursive(File rootDir, List<File> repos, RepoScanCallback cb) {
        if (!isStopped && rootDir.isDirectory()) {
            if (rootDir.getName().equals(".git")) {
                repos.add(rootDir.getParentFile());     // add repo folder to list
                cb.repoFound(rootDir.getParentFile());
            } else {
                cb.dirScanned();
                if (rootDir.listFiles() != null) {
                    for (File childDir : rootDir.listFiles())
                        scanForReposRecursive(childDir, repos, cb);
                }
            }
        }
    }
}
