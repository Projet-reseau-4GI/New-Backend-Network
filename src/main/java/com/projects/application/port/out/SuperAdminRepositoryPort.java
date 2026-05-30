package com.projects.application.port.out;

import com.projects.domain.model.SuperAdmin;
import reactor.core.publisher.Mono;

/**
 * Outbound port — persistence contract for SuperAdmin.
 */
public interface SuperAdminRepositoryPort {
    Mono<SuperAdmin> findByEmail(String email);
    Mono<SuperAdmin> findById(Long id);
    Mono<SuperAdmin> save(SuperAdmin admin);
}
