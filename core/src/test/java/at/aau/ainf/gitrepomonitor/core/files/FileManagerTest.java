package at.aau.ainf.gitrepomonitor.core.files;

import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class FileManagerTest {

    @Test
    public void testSingleton() {
        FileManager fm1 = FileManager.getInstance();
        FileManager fm2 = FileManager.getInstance();

        assertEquals(fm1, fm2);
    }
}
