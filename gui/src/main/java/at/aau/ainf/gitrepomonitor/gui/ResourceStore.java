package at.aau.ainf.gitrepomonitor.gui;

import javafx.scene.image.Image;
import java.util.ResourceBundle;

public abstract class ResourceStore {
    private static ResourceBundle resourceBundle;

    public static void setResourceBundle(ResourceBundle resourceBundle) {
        ResourceStore.resourceBundle = resourceBundle;
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public static String getString(String key, Object... args) {
        String str = resourceBundle.getString(key);
        return String.format(str, args);
    }

    /**
     * Returns the icon at the given path.
     * @param path Icon name/path starting at "icons" directory
     * @return icon as Image
     */
    public static Image getImage(String path) {
        return new Image("/at/aau/ainf/gitrepomonitor/gui/icons/" + path);
    }
}
