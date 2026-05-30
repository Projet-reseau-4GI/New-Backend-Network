package com.projects.application.port.out;

import com.projects.domain.model.Platform;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port — persistence contract for Platform.
 * The domain depends on this interface; infrastructure implements it.
 */
public interface PlatformRepositoryPort {
    Mono<Platform> findById(Long id);
    Mono<Platform> findByEmail(String email);
    Mono<Platform> findByApiKey(String apiKey);
    Mono<Platform> save(Platform platform);
    Flux<Platform> findAll();
    Mono<Long> count();
}
