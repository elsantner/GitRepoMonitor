package at.aau.ainf.gitrepomonitor.core.files.authentication;

import at.aau.ainf.gitrepomonitor.core.files.RepositoryInformation;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;
import java.util.UUID;

/**
 * Abstract wrapper class for authentication credentials.
 */
// required for deserialization as abstract super class
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HttpsCredentials.class, name = "https"),
        @JsonSubTypes.Type(value = SslCredentials.class, name = "ssl"),
        @JsonSubTypes.Type(value = MasterPasswordAuthInfo.class, name = "mp")
})
public abstract class AuthenticationCredentials {
    protected UUID id = UUID.randomUUID();
    protected String name;

    protected AuthenticationCredentials() {
        // for serialization
    }

    protected AuthenticationCredentials(UUID id, String name) {
        this.id = id;
        this.name = name;
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

    @JsonIgnore
    public abstract RepositoryInformation.AuthMethod getAuthMethod();

    /**
     * Destroy any sensitive information (e.g. passwords).
     */
    public abstract void destroy();

    @Override
    public String toString() {
        return name;
    }

    /**
     * Compare credentials based on ID.
     * @param o Other object
     * @return True, if equal
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || id == null || ((AuthenticationCredentials)o).id == null) {
            return false;
        }
        return Objects.equals(id, ((AuthenticationCredentials)o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
