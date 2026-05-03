package yowyob.comops.api.file.application.port.in;

import yowyob.comops.api.file.domain.model.StoredFile;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Mono;

public interface GetStoredFileUseCase {
    Mono<StoredFile> getMetadata(java.util.UUID fileId);
    Mono<Resource> loadResource(java.util.UUID fileId);
}
