package yowyob.comops.api.file.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface StoredFileSpringDataRepository extends ReactiveCrudRepository<StoredFileEntity, UUID> {
    Mono<StoredFileEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
