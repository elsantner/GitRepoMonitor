package at.aau.ainf.gitrepomonitor.gui;

import java.util.ResourceBundle;

public abstract class ResourceStore {
    private static ResourceBundle resourceBundle;

    public static void setResourceBundle(ResourceBundle resourceBundle) {
        ResourceStore.resourceBundle = resourceBundle;
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
}
