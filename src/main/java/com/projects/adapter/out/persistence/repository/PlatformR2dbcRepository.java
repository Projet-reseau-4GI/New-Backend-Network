package com.projects.adapter.out.persistence.repository;

import com.projects.adapter.out.persistence.entity.PlatformEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for PlatformEntity.
 */
public interface PlatformR2dbcRepository extends ReactiveCrudRepository<PlatformEntity, Long> {
    Mono<PlatformEntity> findByApiKey(String apiKey);
    Mono<PlatformEntity> findByEmail(String email);
}
