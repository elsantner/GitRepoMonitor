package at.aau.ainf.gitrepomonitor.core.files;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE)
public class RepositoryInformation implements Comparable<RepositoryInformation>, Cloneable {

    private UUID id;
    private String path;
    private String name;
    private Date modifiedDate;
    private String sslKeyPath;
    private boolean authenticated;
    private AuthMethod authMethod;
    private MergeStrategy mergeStrategy = MergeStrategy.RECURSIVE;
    private int customOrderIndex = -1;

    public enum AuthMethod {
        HTTPS,
        SSL,
        NONE
    }

    public enum MergeStrategy {
        OURS("Ours", org.eclipse.jgit.merge.MergeStrategy.OURS),
        THEIRS("Theirs", org.eclipse.jgit.merge.MergeStrategy.THEIRS),
        RECURSIVE("Recursive", org.eclipse.jgit.merge.MergeStrategy.RECURSIVE);
        // deactivated until further notice
        //RESOLVE("Resolve", org.eclipse.jgit.merge.MergeStrategy.RESOLVE),
        //SIMPLE_TWO_WAY_IN_CORE("Simple 2-Way In", org.eclipse.jgit.merge.MergeStrategy.SIMPLE_TWO_WAY_IN_CORE);

        private final org.eclipse.jgit.merge.MergeStrategy jgitStrat;
        private final String name;
        MergeStrategy(String name, org.eclipse.jgit.merge.MergeStrategy jgitStrat) {
            this.name = name;
            this.jgitStrat = jgitStrat;
        }

        public org.eclipse.jgit.merge.MergeStrategy getJgitStrat() {
            return jgitStrat;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RepoStatus {
        UNCHECKED,
        UP_TO_DATE,
        PATH_INVALID,
        NO_REMOTE,
        INACCESSIBLE_REMOTE,
        WRONG_MASTER_PW,
        PULL_AVAILABLE,
        PUSH_AVAILABLE,
        PULL_PUSH_AVAILABLE,
        MERGE_NEEDED,
        UNKNOWN_ERROR,
    }

    @JsonIgnore
    private RepoStatus status;
    @JsonIgnore
    private boolean persistentValueChanged = false;
    @JsonIgnore
    private int newCommitCount;

    public RepositoryInformation() {
        // generate random UUID upon creation
        // this value is overwritten during deserialization
        this.id = UUID.randomUUID();
        this.status = RepoStatus.UNCHECKED;
    }

    public RepositoryInformation(String path) {
        this(path, null);
    }

    public RepositoryInformation(UUID id) {
        this.id = id;
    }

    public RepositoryInformation(String path, String name) {
        this();
        this.path = path;
        this.name = name;
    }

    public RepositoryInformation(UUID id, String path, String name, MergeStrategy mergeStrat, int orderIdx) {
        this(path, name);
        this.id = id;
        this.mergeStrategy = mergeStrat;
        this.customOrderIndex = orderIdx;
    }

    public UUID getID() {
        return id;
    }

    @JsonIgnore
    public RepoStatus getStatus() {
        return status;
    }
    @JsonIgnore
    public void setStatus(RepoStatus status) {
        this.status = status;
    }
    @JsonIgnore
    public boolean isPersistentValueChanged() {
        return persistentValueChanged;
    }
    @JsonIgnore
    public void setPersistentValueChanged(boolean persistentValueChanged) {
        this.persistentValueChanged = persistentValueChanged;
    }
    @JsonIgnore
    public boolean hasNewChanges() {
        return newCommitCount != 0;
    }
    @JsonIgnore
    public int getNewCommitCount() {
        return newCommitCount;
    }
    @JsonIgnore
    public void setNewChanges(int newCommitCount) {
        this.newCommitCount = newCommitCount;
    }
    @JsonIgnore
    public AuthMethod getAuthMethod() {
        return authMethod;
    }
    @JsonIgnore
    void setAuthMethod(AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
        this.persistentValueChanged = true;
    }

    public String getSslKeyPath() {
        return sslKeyPath;
    }

    public void setSslKeyPath(String sslKeyPath) {
        this.sslKeyPath = sslKeyPath;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        this.persistentValueChanged = true;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.persistentValueChanged = true;
    }
    @JsonIgnore
    public Date getModifiedDate() {
        return modifiedDate;
    }
    @JsonIgnore
    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public MergeStrategy getMergeStrategy() {
        return mergeStrategy;
    }

    public void setMergeStrategy(MergeStrategy mergeStrategy) {
        this.mergeStrategy = mergeStrategy;
        this.persistentValueChanged = true;
    }

    public int getCustomOrderIndex() {
        return customOrderIndex;
    }

    public void setCustomOrderIndex(int customOrderIndex) {
        this.customOrderIndex = customOrderIndex;
        this.persistentValueChanged = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RepositoryInformation that = (RepositoryInformation) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return (name == null || name.isBlank()) ? path : name;
    }

    /**
     * Sort by name first (no/empty names come last), then by path.
     * @param o Other item this item is compared to.
     * @return see standard compareTo() return value
     */
    @Override
    public int compareTo(RepositoryInformation o) {
        if (this.equals(o)) return 0;
        int retVal = 0;
        if (this.getCustomOrderIndex() != -1 && o.getCustomOrderIndex() != -1) {
            retVal = this.getCustomOrderIndex() - o.getCustomOrderIndex();
        } else if (this.getName() != null && !this.getName().isBlank() && o.getName() != null && !o.getName().isEmpty()) {
            retVal = this.getName().compareTo(o.getName());
        } else if (this.getName() != null || o.getName() != null) {
            retVal = ( this.getName() == null || this.getName().isBlank() ) ? 1 : -1;
        }
        if (retVal == 0) {
            retVal = o.getPath().compareTo(this.getPath());
        }
        return retVal;
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
