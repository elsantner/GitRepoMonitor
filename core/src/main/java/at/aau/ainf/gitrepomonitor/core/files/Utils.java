package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;

public abstract class Utils {
    public static boolean validateRepositoryPath(String path) {
        try {
            File dir = new File(path, ".git");
            return dir.exists() && dir.isDirectory();
        } catch (Exception ex) {
            return false;
        }
    }

    public static String getProgramHomeDir() {
        return System.getenv("APPDATA") + "/GitRepoMonitor/";
    }
}
