package Projects.Network.repository;

import Projects.Network.model.EmailVerificationToken;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends ReactiveCrudRepository<EmailVerificationToken, UUID> {
    Mono<EmailVerificationToken> findByUserId(UUID userId);

    Mono<Void> deleteByUserId(UUID userId);

    @Query("DELETE FROM email_verification_tokens WHERE expiry_date < :now")
    Mono<Void> deleteExpiredTokens(Instant now);
}
