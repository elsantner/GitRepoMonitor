package at.aau.ainf.gitrepomonitor.core;

public class TestUtils {
  public static boolean isCleared(char[] array) {
    for (char c : array) {
      if (c != 0) return false;
    }
    return true;
  }
}
