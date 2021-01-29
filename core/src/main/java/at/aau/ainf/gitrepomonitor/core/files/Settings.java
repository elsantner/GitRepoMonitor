package at.aau.ainf.gitrepomonitor.core.files;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

public class Settings implements Cloneable {

    private static XmlMapper mapper;

    private static Settings settings;

    static {
        mapper = XmlMapper.xmlBuilder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
    }

    public static Settings getSettings() {
        if (settings == null) {
            loadSettings();
        }
        return settings;
    }

    private static File getSettingsFile() {
        return new File(StoragePath.getCurrentPath() + "settings.xml");
    }

    public static void storagePathChanged() {
        if (getSettingsFile().exists()) {
            loadSettings();
        } else {
            persist();
        }
    }

    private static void loadSettings() {
        try {
            settings = mapper.readValue(getSettingsFile(), new TypeReference<>() {});
        } catch (IOException e) {
            settings = new Settings();
            persist();
        }
    }

    public static void persist() {
        try {
            if (!getSettingsFile().getParentFile().exists()) {
                getSettingsFile().getParentFile().mkdirs();
            }
            mapper.writeValue(getSettingsFile(), settings);
            Logger.getAnonymousLogger().info("Wrote settings to " + getSettingsFile().getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean cacheEnabled = true;
    private CacheClearMethod clearMethod = CacheClearMethod.NONE;
    private Integer clearValue;

    public enum CacheClearMethod {
        NONE,
        MAX_USES,
        EXPIRATION_TIME
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public CacheClearMethod getClearMethod() {
        return clearMethod;
    }

    public void setClearMethod(CacheClearMethod clearMethod) {
        this.clearMethod = clearMethod;
    }

    public Integer getClearValue() {
        return clearValue;
    }

    public void setClearValue(Integer clearValue) {
        this.clearValue = clearValue;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
