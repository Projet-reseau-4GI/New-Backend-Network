package yowyob.comops.api.file.adapter.out.persistence;

import yowyob.comops.api.file.application.port.out.StoredFileRepository;
import yowyob.comops.api.file.domain.model.StoredFile;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("r2dbc")
public class StoredFileR2dbcRepositoryAdapter implements StoredFileRepository {

    private final StoredFileSpringDataRepository repository;

    public StoredFileR2dbcRepositoryAdapter(StoredFileSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<StoredFile> save(StoredFile storedFile) {
        return repository.save(toEntity(storedFile)).map(this::toDomain);
    }

    @Override
    public Mono<StoredFile> findById(java.util.UUID tenantId, java.util.UUID fileId) {
        return repository.findByIdAndTenantId(fileId, tenantId).map(this::toDomain);
    }

    private StoredFileEntity toEntity(StoredFile storedFile) {
        return new StoredFileEntity(storedFile.id(), storedFile.tenantId(), storedFile.createdAt(),
                storedFile.updatedAt(), storedFile.organizationId(), storedFile.uploadedByUserId(),
                storedFile.fileName(), storedFile.contentType(), storedFile.size(), storedFile.storagePath());
    }

    private StoredFile toDomain(StoredFileEntity entity) {
        return StoredFile.rehydrate(entity.id(), entity.tenantId(), entity.createdAt(), entity.updatedAt(),
                entity.organizationId(), entity.uploadedByUserId(), entity.fileName(), entity.contentType(),
                entity.size(), entity.storagePath());
    }
}
