package at.aau.ainf.gitrepomonitor.core.files;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Objects;

public class RepositoryInformation implements Comparable<RepositoryInformation>, Cloneable {
    private String path;
    private String name;
    private Date dateAdded;
    private boolean pathValid;
    private boolean isUpToDate = true;

    public RepositoryInformation() {
    }

    public RepositoryInformation(String path) {
        this(path, null);
    }

    public RepositoryInformation(String path, String name) {
        this.path = path;
        this.name = name;
    }

    public RepositoryInformation(String path, String name, Date dateAdded) {
        this(path, name);
        this.dateAdded = dateAdded;
    }

    private RepositoryInformation(String path, String name, Date dateAdded, boolean pathValid) {
        this(path, name, dateAdded);
        this.pathValid = pathValid;
    }

    @JsonIgnore
    public boolean isUpToDate() {
        return isUpToDate;
    }

    @JsonIgnore
    public void setUpToDate(boolean upToDate) {
        isUpToDate = upToDate;
    }

    @JsonIgnore
    public boolean isPathValid() {
        return pathValid;
    }

    @JsonIgnore
    public void setPathValid(boolean pathValid) {
        this.pathValid = pathValid;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Date dateAdded) {
        this.dateAdded = dateAdded;
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
