package at.aau.ainf.gitrepomonitor.core.files;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileManagerTest {

    @Test
    public void testSingleton() {
        FileManager fm1 = FileManager.getInstance();
        FileManager fm2 = FileManager.getInstance();

        assertEquals(fm1, fm2);
    }
}
