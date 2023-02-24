package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepoScanCallback;
import at.aau.ainf.gitrepomonitor.core.git.RepoScanner;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;

/**
 * Async task scanning for repos.
 */
public class RepoSearchTask extends Task<Integer> {
    private int scannedDirCount = 0;
    private int foundRepoCount = 0;
    private RepoScanner repoScanner;

    /**
     * Create scanner task
     * @param rootDir Root to scan from (if null then all drives on the PC are scanned)
     * @param excludeNoRemote If true, all Git repos having no remote are NOT returned.
     */
    public RepoSearchTask(File rootDir, boolean excludeNoRemote) {
        this.repoScanner = new RepoScanner(rootDir, excludeNoRemote);
    }

    @Override
    protected Integer call() {
        this.repoScanner.scanForRepos(new RepoScanCallback() {
            @Override
            public void repoFound(File dir) {
                FileManager.getInstance().addToFoundReposAsync(new RepositoryInformation(dir.getPath()),
                        (success, ex) -> {
                            Platform.runLater(() -> {
                                foundRepoCount++;
                                updateStatusMessage();
                            });
                        });
            }

            @Override
            public void dirScanned() {
                scannedDirCount++;
                updateStatusMessage();
            }
        });
        return scannedDirCount;
    }

    /**
     * Set message property according to current status.
     */
    private void updateStatusMessage() {
        updateMessage(ResourceStore.getString("scanpc.scan_status", scannedDirCount, foundRepoCount));
    }

    public boolean isStopped() {
        return repoScanner.isStopped();
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        repoScanner.stop();
    }

    @Override
    public boolean cancel(boolean b) {
        repoScanner.stop();
        return super.cancel(b);
    }

    @Override
    protected void failed() {
        super.failed();
        repoScanner.stop();
    }
}
