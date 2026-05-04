package yowyob.comops.api.file.application.port.in;

import reactor.core.publisher.Mono;
import java.util.UUID;

public interface RecordSystemAuditUseCase {
    default Mono<Void> record(UUID tenantId, UUID organizationId, UUID userId,
                              String action, String entityType,
                              String entityId, String detail) {
        // Version simplifiée : ne fait rien pour l'instant
        return Mono.empty();
    }
}
