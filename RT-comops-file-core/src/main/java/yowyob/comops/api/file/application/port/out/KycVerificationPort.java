package yowyob.comops.api.file.application.port.out;

import reactor.core.publisher.Mono;
import yowyob.comops.api.file.application.port.in.StoreFileCommand;
import java.util.UUID;

public interface KycVerificationPort {
    Mono<Void> verify(UUID tenantId, StoreFileCommand command);
}
