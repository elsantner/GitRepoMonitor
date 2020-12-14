package at.aau.ainf.gitrepomonitor.core.files.authentication;

public class SecureStorageSettings implements Cloneable {
    private boolean cacheEnabled = true;
    private CacheClearMethod clearMethod = CacheClearMethod.NONE;
    private Integer clearValue;
    private boolean useKeyring = true;

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
