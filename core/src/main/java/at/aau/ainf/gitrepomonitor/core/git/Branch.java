package at.aau.ainf.gitrepomonitor.core.git;

import java.util.Objects;

public class Branch implements Comparable<Branch> {
    private String identifier;
    private boolean isRemoteOnly;

    public Branch(String identifier, boolean isRemoteOnly) {
        this.identifier = identifier;
        this.isRemoteOnly = isRemoteOnly;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isRemoteOnly() {
        return isRemoteOnly;
    }

    public void setRemoteOnly(boolean remoteOnly) {
        isRemoteOnly = remoteOnly;
    }

    public String getShortName() {
        return identifier
                .replace("refs/heads/", "")
                .replace("refs/remotes/origin/", "");
    }

    @Override
    public String toString() {
        return getShortName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Branch branch = (Branch) o;
        return isRemoteOnly == branch.isRemoteOnly &&
                Objects.equals(identifier, branch.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, isRemoteOnly);
    }

    @Override
    public int compareTo(Branch o) {
        int retVal = (this.isRemoteOnly != o.isRemoteOnly) ? (this.isRemoteOnly ? 1 : -1) : 0;
        if (retVal == 0) {
            retVal = this.getShortName().compareTo(o.getShortName());
        }
        return retVal;
    }
}
