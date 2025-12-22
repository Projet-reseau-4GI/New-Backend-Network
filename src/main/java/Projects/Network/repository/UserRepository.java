package Projects.Network.repository;

import Projects.Network.model.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository interface for managing User entities.
 * Updated to be Reactive (R2DBC) to match DocumentService.
 */
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

    /**
     * Finds a user by their email address.
     * In Reactive mode, we return Mono instead of Optional.
     */
    Mono<User> findByEmail(String email);

    /**
     * Checks if a user with the given email already exists.
     */
    Mono<Boolean> existsByEmail(String email);

    /**
     * Deletes a user by their email address.
     */
    Mono<Void> deleteByEmail(String email);
}