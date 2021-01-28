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
    private static File fileSettings = new File(Utils.getProgramHomeDir() + "settings.xml");
    private static boolean isFirstUse;

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

    private static void loadSettings() {
        try {
            settings = mapper.readValue(fileSettings, new TypeReference<>() {});
        } catch (IOException e) {
            settings = new Settings();
            isFirstUse = true;
            persist();
        }
    }

    public static void persist() {
        try {
            if (!fileSettings.getParentFile().exists()) {
                fileSettings.getParentFile().mkdirs();
            }
            mapper.writeValue(fileSettings, settings);
            Logger.getAnonymousLogger().info("Wrote settings to " + fileSettings.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static boolean isFirstUse() {
        return isFirstUse;
    }

    private boolean cacheEnabled = true;
    private CacheClearMethod clearMethod = CacheClearMethod.NONE;
    private Integer clearValue;
    private boolean useKeyring = true;
    private String storagePath;

    public enum CacheClearMethod {
        NONE,
        MAX_USES,
        EXPIRATION_TIME
    }

    public Settings() {
        storagePath = Utils.getProgramHomeDir();
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        if (storagePath != null) {
            this.storagePath = storagePath;
        }
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

    public boolean isUseKeyring() {
        return useKeyring;
    }

    public void setUseKeyring(boolean useKeyring) {
        this.useKeyring = useKeyring;
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
