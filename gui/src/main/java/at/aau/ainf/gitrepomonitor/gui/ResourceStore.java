package at.aau.ainf.gitrepomonitor.gui;

import javafx.scene.image.Image;
import java.util.ResourceBundle;

/**
 * Provides easy access to resources (icons, strings).
 */
public abstract class ResourceStore {
    private static ResourceBundle resourceBundle;

    /**
     * Set the current resource bundle to use.
     * @param resourceBundle Resource bundle
     */
    public static void setResourceBundle(ResourceBundle resourceBundle) {
        ResourceStore.resourceBundle = resourceBundle;
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    /**
     * Get the formatted string from the resource bundle.
     * @param key String key
     * @param args Arguments for this string
     * @return Formatted string
     */
    public static String getString(String key, Object... args) {
        String str = resourceBundle.getString(key);
        return String.format(str, args);
    }

    /**
     * Get the icon at the given path.
     * @param path Icon name/path starting at "icons" directory
     * @return icon as Image
     */
    public static Image getImage(String path) {
        return new Image("/at/aau/ainf/gitrepomonitor/gui/icons/" + path);
    }
}
