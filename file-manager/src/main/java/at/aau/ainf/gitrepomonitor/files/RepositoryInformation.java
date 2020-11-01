package at.aau.ainf.gitrepomonitor.files;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Objects;

public class RepositoryInformation implements Comparable<RepositoryInformation>, Cloneable {
    private String path;
    private String name;
    private Date dateAdded;
    private boolean pathValid;

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

    @Override
    public int compareTo(RepositoryInformation o) {
        if (this.equals(o)) return 0;
        int retVal = 0;
        if (this.getName() != null && o.getName() != null) {
            retVal = this.getName().compareTo(o.getName());
        } else if (this.getName() != null || o.getName() != null) {
            retVal = this.getName() == null ? 1 : -1;
        }
        if (retVal == 0) {
            retVal = o.getPath().compareTo(this.getName());
        }
        return retVal;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
