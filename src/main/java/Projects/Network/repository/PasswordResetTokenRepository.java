package Projects.Network.repository;

import Projects.Network.model.PasswordResetToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * PasswordResetTokenRepository
 *
 * Repository for managing PasswordResetToken entities.
 */
@Repository
public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken, UUID> {

    Mono<PasswordResetToken> findByToken(String token);

    Mono<Void> deleteByUserId(UUID userId);
    
    Mono<PasswordResetToken> findByUserId(UUID userId);
}
