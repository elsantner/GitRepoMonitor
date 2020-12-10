package at.aau.ainf.gitrepomonitor.core.files.authentication;

public class SecureStorageSettings {
    private boolean cacheEnabled = true;
    private CacheClearMethod clearMethod = CacheClearMethod.NONE;
    private int clearValue;

    protected enum CacheClearMethod {
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

    public int getClearValue() {
        return clearValue;
    }

    public void setClearValue(int clearValue) {
        this.clearValue = clearValue;
    }
}
