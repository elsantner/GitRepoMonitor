package at.aau.ainf.gitrepomonitor;

import at.aau.ainf.gitrepomonitor.gui.GUIStarter;

/**
 * This plain Main class is required since a deployed JavaFX application may
 * have problems launching from a class extending "javafx.application.Application".
 */
public class Main {
    public static void main(final String[] args) {
        GUIStarter.main(args);
    }

}