package com.projects.adapter.out.persistence.repository;

import com.projects.adapter.out.persistence.entity.SuperAdminEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for SuperAdminEntity.
 */
public interface SuperAdminR2dbcRepository extends ReactiveCrudRepository<SuperAdminEntity, Long> {
    Mono<SuperAdminEntity> findByEmail(String email);
}
