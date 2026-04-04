package Projects.Network.repository;

import Projects.Network.model.DocumentEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * DocumentRepository
 *
 * Repository interface responsible for data access operations
 * related to {@link DocumentEntity}.
 *
 * This interface extends R2dbcRepository to provide reactive
 * CRUD operations for the "documents" table.
 *
 * Spring Data R2DBC automatically generates the implementation
 * of this repository at runtime based on method signatures.
 *
 * This repository belongs to the Repository layer and must not
 * contain any business logic.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
public interface DocumentRepository extends R2dbcRepository<DocumentEntity, UUID> {

    /**
     * @param platformId the unique identifier of the platform
     * @return a Flux emitting all DocumentEntity objects belonging to the platform
     */
    Flux<DocumentEntity> findAllByPlatformId(Long platformId);
}
