package yowyob.comops.api.kernel.application.service;

import reactor.core.publisher.Mono;
import java.util.UUID;

public class ReactiveRequestContextHolder {

    public record RequestContext(UUID tenantId, UUID organizationId, UUID userId) {}

    public static Mono<RequestContext> getRequiredContext() {
        return Mono.just(new RequestContext(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003")
        ));
    }
}
