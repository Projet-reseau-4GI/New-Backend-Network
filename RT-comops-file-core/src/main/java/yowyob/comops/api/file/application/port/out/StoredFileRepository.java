package yowyob.comops.api.file.application.port.out;

import yowyob.comops.api.file.domain.model.StoredFile;
import reactor.core.publisher.Mono;

public interface StoredFileRepository {
    Mono<StoredFile> save(StoredFile storedFile);
    Mono<StoredFile> findById(java.util.UUID tenantId, java.util.UUID fileId);
}
