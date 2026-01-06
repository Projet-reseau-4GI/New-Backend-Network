package Projects.Network.repository;

import Projects.Network.model.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * UserRepository
 *
 * Repository interface responsible for managing persistence
 * operations related to {@link User} entities.
 *
 * This repository uses a reactive programming model based on
 * Spring Data R2DBC and provides non-blocking CRUD operations.
 *
 * The implementation of this interface is automatically generated
 * by Spring Data at runtime.
 *
 * This repository belongs to the Repository layer and must not
 * contain any business logic.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

    /**
     * Retrieves a user using their email address.
     *
     * This method is commonly used during authentication
     * and user lookup operations.
     *
     * The result is returned as a Mono, which may emit
     * zero or one User instance.
     *
     * @param email the email address of the user
     * @return a Mono emitting the User if found
     */
    Mono<User> findByEmail(String email);

    /**
     * Checks whether a user with the given email already exists.
     *
     * This method is typically used to prevent duplicate
     * user registrations.
     *
     * @param email the email address to check
     * @return a Mono emitting true if a user exists, false otherwise
     */
    Mono<Boolean> existsByEmail(String email);

    /**
     * Deletes a user using their email address.
     *
     * This operation removes the corresponding user record
     * from the database in a reactive and non-blocking manner.
     *
     * @param email the email address of the user to delete
     * @return a Mono signaling completion of the delete operation
     */
    Mono<Void> deleteByEmail(String email);
}
