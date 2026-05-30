package com.projects.adapter.out.persistence.repository;

import com.projects.adapter.out.persistence.entity.VerificationLogEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Spring Data R2DBC repository for VerificationLogEntity.
 */
public interface VerificationLogR2dbcRepository extends ReactiveCrudRepository<VerificationLogEntity, Long> {
}
