package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

public interface RepoScanCallback {
    void repoFound(File dir);
    void dirScanned();
}
