package com.projects.adapter.out.persistence.adapter;

import com.projects.domain.model.SuperAdmin;
import com.projects.application.port.out.SuperAdminRepositoryPort;
import com.projects.adapter.out.persistence.mapper.SuperAdminMapper;
import com.projects.adapter.out.persistence.repository.SuperAdminR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter — implements SuperAdminRepositoryPort using Spring Data R2DBC.
 */
@Component
@RequiredArgsConstructor
public class SuperAdminRepositoryAdapter implements SuperAdminRepositoryPort {

    private final SuperAdminR2dbcRepository repository;
    private final SuperAdminMapper mapper;

    @Override
    public Mono<SuperAdmin> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Mono<SuperAdmin> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<SuperAdmin> save(SuperAdmin admin) {
        return repository.save(mapper.toEntity(admin)).map(mapper::toDomain);
    }
}
