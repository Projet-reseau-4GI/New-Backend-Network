package com.projects.config.kernel;

import com.projects.adapter.out.kernel.JwksTokenValidator;
import com.projects.adapter.out.kernel.KernelTokenClaims;
import com.projects.config.ReactiveTenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Filtre WebFlux aligné sur le TenantWebFilter du kernel-core.
 *
 * Il lit les headers kernel entrants :
 *   X-Tenant-Id       → tenantId
 *   X-Organization-Id → organizationId
 *   X-Agency-Id       → agencyId
 *   Authorization     → Bearer <JWT RS256> (optionnel)
 *
 * Quand le JWT RS256 est présent ET que rs256-enabled=true, il est validé
 * via le JWKS du kernel et les claims sont injectés dans le contexte réactif.
 *
 * Si rs256-enabled=false (mode migration), le filtre propage uniquement
 * les headers de contexte sans valider le JWT via le kernel.
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class KernelTenantContextFilter implements WebFilter {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_ORG_ID    = "X-Organization-Id";
    public static final String HEADER_AGENCY_ID = "X-Agency-Id";
    public static final String HEADER_AUTH      = "Authorization";

    private final KernelClientProperties kernelProperties;
    private final JwksTokenValidator jwksTokenValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        UUID tenantId       = parseUuidHeader(request, HEADER_TENANT_ID);
        UUID organizationId = parseUuidHeader(request, HEADER_ORG_ID);
        UUID agencyId       = parseUuidHeader(request, HEADER_AGENCY_ID);
        String bearer       = extractBearer(request);

        // Mode RS256 activé : valide le JWT et enrichit le contexte avec les claims kernel
        if (kernelProperties.getJwt().isRs256Enabled() && bearer != null) {
            return jwksTokenValidator.validateAndExtract(bearer)
                    .map(claims -> mergeWithHeaders(claims, tenantId, organizationId, agencyId))
                    .switchIfEmpty(Mono.defer(() -> {
                        // Token invalide mais contexte headers potentiellement présent
                        if (tenantId != null) {
                            return Mono.just(headersOnlyClaims(tenantId, organizationId, agencyId));
                        }
                        return Mono.empty();
                    }))
                    .flatMap(claims ->
                            chain.filter(exchange)
                                    .contextWrite(ctx -> ReactiveTenantContext.putKernelClaims(ctx, claims)))
                    .switchIfEmpty(chain.filter(exchange));
        }

        // Mode migration (rs256-enabled=false) : propage uniquement les headers sans valider
        if (tenantId != null) {
            KernelTokenClaims headersCtx = headersOnlyClaims(tenantId, organizationId, agencyId);
            return chain.filter(exchange)
                    .contextWrite(ctx -> ReactiveTenantContext.putKernelClaims(ctx, headersCtx));
        }

        return chain.filter(exchange);
    }

    private KernelTokenClaims mergeWithHeaders(KernelTokenClaims claims,
                                                UUID tenantId,
                                                UUID organizationId,
                                                UUID agencyId) {
        UUID effectiveTenant = tenantId != null ? tenantId : claims.tenantId();
        UUID effectiveOrg    = organizationId != null ? organizationId : claims.organizationId();
        UUID effectiveAgency = agencyId != null ? agencyId : claims.agencyId();
        return new KernelTokenClaims(
                claims.userId(),
                effectiveTenant,
                effectiveOrg,
                effectiveAgency,
                claims.actorId(),
                claims.subject(),
                claims.email()
        );
    }

    private KernelTokenClaims headersOnlyClaims(UUID tenantId, UUID organizationId, UUID agencyId) {
        return new KernelTokenClaims(null, tenantId, organizationId, agencyId, null, null, null);
    }

    private UUID parseUuidHeader(ServerHttpRequest request, String headerName) {
        List<String> values = request.getHeaders().get(headerName);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(values.getFirst().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String extractBearer(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HEADER_AUTH);
        if (auth == null || auth.isBlank() || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return auth.substring(7).trim();
    }
}
