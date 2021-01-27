package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;

import java.util.Objects;
import java.util.UUID;

public abstract class AuthenticationInformation {
    protected UUID id = UUID.randomUUID();
    protected String name;

    protected AuthenticationInformation() {
        // for serialization
    }

    protected AuthenticationInformation(UUID id, String name) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getID() {
        return id;
    }

    public void setID(UUID id) {
        this.id = id;
    }

    public abstract RepositoryInformation.AuthMethod getAuthMethod();

    public abstract void destroy();

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || id == null || ((AuthenticationInformation)o).id == null) {
            return false;
        }
        return Objects.equals(id, ((AuthenticationInformation)o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
