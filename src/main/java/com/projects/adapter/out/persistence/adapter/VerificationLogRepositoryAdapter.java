package com.projects.adapter.out.persistence.adapter;

import com.projects.domain.model.VerificationLog;
import com.projects.application.port.out.VerificationLogRepositoryPort;
import com.projects.adapter.out.persistence.mapper.VerificationLogMapper;
import com.projects.adapter.out.persistence.repository.VerificationLogR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter — implements VerificationLogRepositoryPort using Spring Data R2DBC.
 */
@Component
@RequiredArgsConstructor
public class VerificationLogRepositoryAdapter implements VerificationLogRepositoryPort {

    private final VerificationLogR2dbcRepository repository;
    private final VerificationLogMapper mapper;

    @Override
    public Mono<VerificationLog> save(VerificationLog log) {
        return repository.save(mapper.toEntity(log)).map(mapper::toDomain);
    }

    @Override
    public Flux<VerificationLog> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Mono<VerificationLog> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
