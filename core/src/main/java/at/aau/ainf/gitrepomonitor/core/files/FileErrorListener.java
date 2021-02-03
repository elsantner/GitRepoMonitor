package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

/**
 * Listener interface for file related errors.
 */
public interface FileErrorListener {
    void fileUnavailable(File path);
}
