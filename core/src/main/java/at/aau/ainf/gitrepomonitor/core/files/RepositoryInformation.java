package at.aau.ainf.gitrepomonitor.core.files;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE)
public class RepositoryInformation implements Comparable<RepositoryInformation>, Cloneable {
    private String path;
    private String name;
    private Date dateAdded;

    public enum RepoStatus {
        UP_TO_DATE,
        PATH_INVALID,
        NO_REMOTE,
        INACCESSIBLE_REMOTE,
        PULL_AVAILABLE,
        PUSH_AVAILABLE,
        MERGE_NEEDED
    }
    @JsonIgnore
    private RepoStatus status;
    @JsonIgnore
    private boolean persistentValueChanged = false;

    public RepositoryInformation() {
        this.status = RepoStatus.UP_TO_DATE;
    }

    public RepositoryInformation(String path) {
        this(path, null);
    }

    public RepositoryInformation(String path, String name) {
        this();
        this.path = path;
        this.name = name;
    }

    public RepositoryInformation(String path, String name, Date dateAdded) {
        this(path, name);
        this.dateAdded = dateAdded;
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
        this.persistentValueChanged = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.persistentValueChanged = true;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
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
        if (this.getName() != null && !this.getName().isBlank() && o.getName() != null && !o.getName().isEmpty()) {
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
