package Projects.Network.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Entity representing the User in the system.
 * This class maps the Java object to the 'users' table in the PostgreSQL database.
 * It follows the structure: id-utilisateur, nom, prenom, email.
 */
@Data // Generates getters, setters, toString, equals, and hashCode methods automatically
@Builder // Implements the Builder pattern for easy object creation
@NoArgsConstructor // Generates a no-argument constructor (required by many frameworks)
@AllArgsConstructor // Generates a constructor with all fields as arguments
@Table("users")// Maps this entity to the 'users' table in the database
@JsonIgnoreProperties(ignoreUnknown = true)
public class User
{

    /**
     * The unique identifier for the user.
     * Maps to 'user_id' column and acts as the Primary Key.
     */
    @Id
    @Column("user_id")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID userId;        // id-utilisateur (System assigned)

    /**
     * The user's family name (surname).
     */
    @Column("last_name")
    private String lastName;    // nom

    /**
     * The user's given name.
     */
    @Column("first_name")
    private String firstName;   // prenom

    /**
     * The user's electronic mail address.
     */
    @Column("email")
    private String email;// email

    @Column("password")
    private String password;    // Added for authentication security

    /* * Explicit getter methods.
     * While @Data provides these, keeping them ensures compatibility
     * and clarity within reactive streams.
     */

    /** @return the user's first name */
    public String getFirstName() { return firstName; }

    /** @return the user's last name */
    public String getLastName() { return lastName; }

    /** @return the unique UUID of the user */
    public UUID getUserId() { return userId; }
}