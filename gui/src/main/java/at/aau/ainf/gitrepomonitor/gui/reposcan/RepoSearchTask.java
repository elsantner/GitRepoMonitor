package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.core.files.FileManager;
import at.aau.ainf.gitrepomonitor.core.files.RepoScanCallback;
import at.aau.ainf.gitrepomonitor.core.files.RepoScanner;
import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import at.aau.ainf.gitrepomonitor.gui.ResourceStore;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;

public class RepoSearchTask extends Task<Integer> {
    private File rootDir;
    private int scannedDirCount = 0;
    private int foundRepoCount = 0;
    private RepoScanner repoScanner;

    public RepoSearchTask(File rootDir) {
        this.rootDir = rootDir;
        this.repoScanner = new RepoScanner(rootDir);
    }

    @Override
    protected Integer call() {
        this.repoScanner.scanForRepos(new RepoScanCallback() {
            @Override
            public void repoFound(File dir) {
                Platform.runLater(() -> {
                    FileManager.getInstance().addToFoundRepos(new RepositoryInformation(dir.getPath()));
                    foundRepoCount++;
                });
            }

            @Override
            public void dirScanned() {
                updateMessage(String.format(ResourceStore.getResourceBundle().getString("scanpc.scan_status"),
                        (++scannedDirCount), foundRepoCount));
            }
        });
        return scannedDirCount;
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
