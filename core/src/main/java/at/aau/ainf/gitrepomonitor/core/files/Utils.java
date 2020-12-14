package at.aau.ainf.gitrepomonitor.core.files;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

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

    public static char[] toCharOrNull(String str) {
        return str != null ? str.toCharArray() : null;
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

    private static byte[] toBytes(char[] chars) {
        CharBuffer bufChar = CharBuffer.wrap(chars);
        ByteBuffer bufByte = StandardCharsets.UTF_8.encode(bufChar);
        byte[] bytes = Arrays.copyOfRange(bufByte.array(),
                bufByte.position(), bufByte.limit());
        Arrays.fill(bufByte.array(), (byte) 0);
        return bytes;
    }
}
