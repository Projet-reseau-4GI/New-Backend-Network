package com.projects.adapter.out.persistence.adapter;

import com.projects.domain.model.Platform;
import com.projects.application.port.out.PlatformRepositoryPort;
import com.projects.adapter.out.persistence.mapper.PlatformMapper;
import com.projects.adapter.out.persistence.repository.PlatformR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound adapter — implements PlatformRepositoryPort using Spring Data R2DBC.
 */
@Component
@RequiredArgsConstructor
public class PlatformRepositoryAdapter implements PlatformRepositoryPort {

    private final PlatformR2dbcRepository repository;
    private final PlatformMapper mapper;

    @Override
    public Mono<Platform> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Platform> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Mono<Platform> findByApiKey(String apiKey) {
        return repository.findByApiKey(apiKey).map(mapper::toDomain);
    }

    @Override
    public Mono<Platform> save(Platform platform) {
        return repository.save(mapper.toEntity(platform)).map(mapper::toDomain);
    }

    @Override
    public Flux<Platform> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Mono<Long> count() {
        return repository.count();
    }
}
