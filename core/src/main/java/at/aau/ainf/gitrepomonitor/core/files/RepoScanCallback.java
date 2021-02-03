package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

/**
 * Callback for repository scanner.
 */
public interface RepoScanCallback {
    void repoFound(File dir);
    void dirScanned();
}
