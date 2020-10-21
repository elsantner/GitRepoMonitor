package at.aau.ainf.gitrepomonitor.gui.reposcan;

import at.aau.ainf.gitrepomonitor.files.RepoScanner;
import at.aau.ainf.gitrepomonitor.files.RepoScanCallback;
import at.aau.ainf.gitrepomonitor.files.RepositoryInformation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import java.io.File;
import java.util.ArrayList;

public class RepoSearchTask extends Task<Integer> {
    private File rootDir;
    private int scannedDirCount = 0;
    private ObservableList<RepositoryInformation> foundRepos;
    private RepoScanner repoScanner;

    public RepoSearchTask(File rootDir) {
        this.rootDir = rootDir;
        this.repoScanner = new RepoScanner(rootDir);
        this.foundRepos = FXCollections.observableList(new ArrayList<>());
    }

    public ObservableList<RepositoryInformation> getFoundRepos() {
        return foundRepos;
    }

    @Override
    protected Integer call() {
        this.repoScanner.scanForRepos(new RepoScanCallback() {
            @Override
            public void repoFound(File dir) {
                Platform.runLater(() -> foundRepos.add(new RepositoryInformation(dir.getPath())));
            }

            @Override
            public void dirScanned() {
                updateMessage("Directories scanned: " + (++scannedDirCount) + " | Repos found: " + foundRepos.size());
            }
        });
        return scannedDirCount;
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
