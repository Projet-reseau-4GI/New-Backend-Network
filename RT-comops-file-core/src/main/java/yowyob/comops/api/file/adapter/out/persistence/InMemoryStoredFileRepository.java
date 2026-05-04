package yowyob.comops.api.file.adapter.out.persistence;

import yowyob.comops.api.file.application.port.out.StoredFileRepository;
import yowyob.comops.api.file.domain.model.StoredFile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("test-memory")
public class InMemoryStoredFileRepository implements StoredFileRepository {

    private final Map<UUID, StoredFile> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<StoredFile> save(StoredFile storedFile) {
        return Mono.fromSupplier(() -> {
            storage.put(storedFile.id(), storedFile);
            return storedFile;
        });
    }

    @Override
    public Mono<StoredFile> findById(UUID tenantId, UUID fileId) {
        return Mono.justOrEmpty(storage.get(fileId)).filter(file -> file.tenantId().equals(tenantId));
    }
}
