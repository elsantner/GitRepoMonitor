package at.aau.ainf.gitrepomonitor.files;

import java.io.File;

public interface RepoScanCallback {
    void repoFound(File dir);
    void dirScanned();
}
