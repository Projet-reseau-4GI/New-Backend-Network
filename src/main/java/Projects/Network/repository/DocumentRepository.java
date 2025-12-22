package Projects.Network.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import Projects.Network.model.DocumentEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DocumentRepository extends R2dbcRepository<DocumentEntity, UUID> {

    /**
     * Retrieves a document entity by its storage path in MinIO.
     *

     * @return Mono emitting the entity if found, empty otherwise
     */
    Mono<DocumentEntity> findByMinioPath(String minioPath);
    // This method allows future retrieval of documents by their MinIO path
}