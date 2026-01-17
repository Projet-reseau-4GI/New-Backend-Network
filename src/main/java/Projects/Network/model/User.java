package Projects.Network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * User
 *
 * This entity represents a user of the system.
 * It is mapped to the "users" table in the database using Spring Data R2DBC.
 *
 * The purpose of this class is to define how user-related data is persisted
 * and retrieved from the database. It contains only structural information
 * and must not include any business logic.
 *
 * This entity is typically used by the authentication, authorization,
 * and user management modules of the application.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("users")
public class User {

    /**
     * Unique identifier of the user.
     *
     * This field represents the primary key of the "users" table.
     * It is annotated with @Id to indicate that it is managed by
     * Spring Data R2DBC.
     */
    @Id
    @Column("user_id")
    private UUID userId;

    /**
     * Email address of the user.
     *
     * This value is used as a unique identifier for authentication
     * and communication purposes.
     */
    @Column("email")
    private String email;

    /**
     * Encrypted password of the user.
     *
     * This field stores the hashed version of the user's password.
     * Plain text passwords must never be stored for security reasons.
     */
    @Column("password")
    private String password;

    /**
     * First name of the user.
     *
     * This field contains the given name used for identification
     * and display purposes.
     */
    @Column("first_name")
    private String firstName;

    /**
     * Last name of the user.
     *
     * This field contains the family name of the user.
     */
    @Column("last_name")
    private String lastName;

    /**
     * Timestamp indicating when the user account was created.
     *
     * This value is generally set automatically at the time
     * of user registration.
     */
    @Column("created_at")
    private Instant createdAt;
}
