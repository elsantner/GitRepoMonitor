package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;

/**
 * This class manages the path for all other program data (data.db and settings.xml).
 * The file "path" must however remain at the default location!
 */
public class StoragePath {

    private static String currentPath;
    private static boolean isFirstUse;

    /**
     * Get the path where the file containing the path information is stored.
     * @return Path file path
     */
    public static String getFilePath() {
        String path = System.getenv("APPDATA") + "/GitRepoMonitor/";
        return separatorsToSystem(path);
    }

    /**
     * Get the current path where data.db and settings.xml are stored.
     * @return Current storage path.
     */
    public static String getCurrentPath() {
        if (currentPath == null) {
            try {
                loadCurrentPath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return addConcludingSeparator(currentPath);
    }

    /**
     * Check if the program is executed "for the first time" (i.e. if no "path" file existed).
     * @return True, if program is executed "for the first time".
     */
    public static boolean isFirstUse() {
        if (currentPath == null) {
            try {
                loadCurrentPath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return isFirstUse;
    }

    public static void resetToDefaultPath() throws IOException {
        currentPath = getFilePath() + "data/";
        new File(currentPath).mkdirs();
        setCurrentPath(currentPath);
    }

    /**
     * Loads the current path from file "path".
     * If file does not exist or is not accessible, reset to default path.
     * @throws IOException
     */
    private static void loadCurrentPath() throws IOException {
        File pathFile = new File(getFilePath() + "path");
        if (pathFile.exists() && pathFile.isFile() && pathFile.canRead()) {
            try {
                currentPath = Files.readString(pathFile.toPath());
            } catch (IOException e) {
                currentPath = getFilePath() + "data/";
                new File(currentPath).mkdirs();
                setCurrentPath(currentPath);
            }
        } else {
            isFirstUse = true;
            currentPath = getFilePath() + "data/";
            new File(currentPath).mkdirs();
            setCurrentPath(currentPath);
        }
    }

    /**
     * Set the current data path and persist to file.
     * The path must be an existing directory and must have read and write permissions.
     * @param path New storage path.
     * @throws IOException
     */
    public static void setCurrentPath(String path) throws IOException {
        File newPathFile = new File(path);
        if (!newPathFile.exists() || !newPathFile.isDirectory()) {
            throw new FileNotFoundException("New directory does not exists");
        }
        if (!newPathFile.canRead() || !newPathFile.canWrite()) {
            throw new AccessDeniedException("Directory must have read and write permissions");
        }

        currentPath = addConcludingSeparator(path);
        File pathFile = new File(getFilePath() + "path");
        Files.writeString(pathFile.toPath(), currentPath);
    }

    public static String separatorsToSystem(String res) {
        if (res == null) return null;
        if (File.separatorChar=='\\') {
            // From Windows to Linux/Mac
            return res.replace('/', File.separatorChar);
        } else {
            // From Linux/Mac to Windows
            return res.replace('\\', File.separatorChar);
        }
    }

    public static String addConcludingSeparator(String path) {
        path = separatorsToSystem(path);
        if (path.charAt(path.length()-1) != File.separatorChar) {
            path += File.separatorChar;
        }
        return path;
    }

    /**
     * Check if a directory contains both data.db and settings.xml files.
     * @param path Directory to check
     * @return True, if both files exist.
     */
    public static boolean containsProgramFiles(String path) {
        File dbFile = new File(addConcludingSeparator(path) + "data.db");
        File settingsFile = new File(addConcludingSeparator(path) + "settings.xml");

        return dbFile.exists() && settingsFile.exists();
    }
}
