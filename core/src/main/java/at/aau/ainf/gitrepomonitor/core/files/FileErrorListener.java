package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

public interface FileErrorListener {
    void fileUnavailable(File path);
}
