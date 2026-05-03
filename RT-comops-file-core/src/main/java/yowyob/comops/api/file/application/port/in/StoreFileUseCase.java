package yowyob.comops.api.file.application.port.in;

import yowyob.comops.api.file.domain.model.StoredFile;
import reactor.core.publisher.Mono;

public interface StoreFileUseCase {
    Mono<StoredFile> store(StoreFileCommand command);
}
