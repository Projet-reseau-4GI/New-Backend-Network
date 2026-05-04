package yowyob.comops.api.file.application.service;

import org.springframework.web.reactive.result.view.RequestContext;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class ReactiveRequestContextHolder {

    public record RequestContext(UUID tenantId, UUID organizationId, UUID userId) {}

    private static final ThreadLocal<RequestContext> context = new ThreadLocal<>();

    public static Mono<RequestContext> getRequiredContext() {
        // Version simplifiée : contexte par défaut pour les tests
        // À remplacer plus tard par la vraie logique multi-tenant
        return Mono.just(new RequestContext(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        ));
    }
}