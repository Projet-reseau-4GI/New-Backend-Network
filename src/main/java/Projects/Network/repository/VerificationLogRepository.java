package Projects.Network.repository;

import Projects.Network.model.VerificationLog;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface VerificationLogRepository extends ReactiveCrudRepository<VerificationLog, Long> {
    
    /**
     * Find all verification logs of a specific platform.
     * @param platformId the platform ID
     * @return Flux emitting VerificationLogs
     */
    Flux<VerificationLog> findByPlatformId(Long platformId);
}

