package Projects.Network.repository;

import Projects.Network.model.Platform;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PlatformRepository extends ReactiveCrudRepository<Platform, Long> {
    
    /**
     * Find a Platform by its unique API Key.
     * @param apiKey the API key
     * @return Mono of Platform
     */
    Mono<Platform> findByApiKey(String apiKey);

    /**
     * Find a Platform by its unique email.
     * @param email the email
     * @return Mono of Platform
     */
    Mono<Platform> findByEmail(String email);
    
    
}
