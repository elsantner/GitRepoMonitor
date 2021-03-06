package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.*;

/**
 * Utility functionality.
 */
public abstract class Utils {
    /**
     * Get last modified date of any file in a directory
     * @param path Directory to analyze.
     * @return
     */
    public static Date getLastChangedDate(String path) {
        File dir = new File(path);
        if (dir.isDirectory()) {
            Optional<File> opFile = Arrays.stream(Objects.requireNonNull(dir.listFiles(File::isFile)))
                    .max(Comparator.comparingLong(File::lastModified));

            if (opFile.isPresent()){
                return new Date(opFile.get().lastModified());
            }
        }
        return new Date(0);
    }

    /**
     * Check if the provided path contains a directory '.git'
     * @param path
     * @return
     */
    public static boolean validateRepositoryPath(String path) {
        try {
            File dir = new File(path, ".git");
            return dir.exists() && dir.isDirectory();
        } catch (Exception ex) {
            return false;
        }
    }

    public static void clearArray(char[] a) {
        if (a != null) {
            Arrays.fill(a, (char) 0);
        }
    }

    public static void clearArray(byte[] a) {
        if (a != null) {
            Arrays.fill(a, (byte) 0);
        }
    }

    public static char[] toCharOrNull(String str) {
        return str != null ? str.toCharArray() : null;
    }

    public static byte[] toBytesOrNull(String str) {
        return str != null ? str.getBytes() : null;
    }

    public static char[] sha3_256(char[] m) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(toBytes(m));
            return bytesToHex(hash).toCharArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexStr = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexStr.append('0');
            }
            hexStr.append(hex);
        }
        return hexStr.toString();
    }

    /**
     * Convert character array to byte array
     * @param chars
     * @return
     */
    private static byte[] toBytes(char[] chars) {
        CharBuffer bufChar = CharBuffer.wrap(chars);
        ByteBuffer bufByte = StandardCharsets.UTF_8.encode(bufChar);
        byte[] bytes = Arrays.copyOfRange(bufByte.array(),
                bufByte.position(), bufByte.limit());
        Arrays.fill(bufByte.array(), (byte) 0);
        return bytes;
    }

    /**
     * Get the deepest existing directory in the provided path.
     * @param path
     * @return
     */
    public static File getDeepestExistingDirectory(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        File dir = new File(path);
        while (dir != null && (!dir.exists() || !dir.isDirectory())) {
            dir = dir.getParentFile();
        }
        return dir;
    }

    public static String toStringOrNull(Object o) {
        if (o != null)
            return o.toString();
        else
            return null;
    }
}
