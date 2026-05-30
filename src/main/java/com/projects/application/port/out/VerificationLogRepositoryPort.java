package com.projects.application.port.out;

import com.projects.domain.model.VerificationLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port — persistence contract for VerificationLog.
 */
public interface VerificationLogRepositoryPort {
    Mono<VerificationLog> save(VerificationLog log);
    Flux<VerificationLog> findAll();
    Mono<VerificationLog> findById(Long id);
}
