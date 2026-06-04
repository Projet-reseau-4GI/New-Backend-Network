package com.projects.adapter.out.kernel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur HTTP vers l'organization-core du Kernel RT-Comops.
 *
 * Responsabilités :
 *  1. Vérifier qu'une organisation est bien abonnée au service KYC
 *     avant d'autoriser une vérification de document.
 *  2. Récupérer les informations d'une organisation.
 *
 * Endpoint utilisé :
 *   GET /api/organizations/{orgId}/service-entitlements
 *
 * Codes d'erreur kernel possibles :
 *   ORGANIZATION_SERVICE_NOT_SUBSCRIBED → org non abonnée
 *   ORGANIZATION_SERVICE_QUOTA_EXCEEDED → quota fair-use dépassé
 */
@Service
@Slf4j
public class KernelOrganizationService {

    private static final String ORGANIZATIONS_BASE = "/api/organizations";

    private final WebClient kernelWebClient;

    public KernelOrganizationService(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * Vérifie que l'organisation est abonnée au serviceCode donné.
     *
     * @param tenantId    UUID du tenant
     * @param orgId       UUID de l'organisation
     * @param serviceCode Code du service (ex: "KYC")
     * @param bearerToken JWT RS256 de l'utilisateur
     * @return Mono<Boolean> — true si abonnée, false sinon
     */
    public Mono<Boolean> isSubscribedToService(UUID tenantId, UUID orgId, String serviceCode, String bearerToken) {
        return kernelWebClient.get()
                .uri(ORGANIZATIONS_BASE + "/" + orgId + "/service-entitlements")
                .headers(h -> applyKernelHeaders(h, tenantId, orgId, bearerToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object data = response.get("data");
                    if (data instanceof List<?> services) {
                        return services.stream().anyMatch(s -> {
                            if (s instanceof Map<?, ?> entry) {
                                Object code = entry.get("serviceCode");
                                Object active = entry.get("active");
                                return serviceCode.equals(code) && Boolean.TRUE.equals(active);
                            }
                            return false;
                        });
                    }
                    return false;
                })
                .onErrorResume(e -> {
                    log.warn("[org-core] Impossible de vérifier l'entitlement org={} service={} : {}",
                            orgId, serviceCode, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Récupère les informations de base d'une organisation depuis l'organization-core.
     */
    public Mono<OrganizationSummary> getOrganization(UUID tenantId, UUID orgId, String bearerToken) {
        return kernelWebClient.get()
                .uri(ORGANIZATIONS_BASE + "/" + orgId)
                .headers(h -> applyKernelHeaders(h, tenantId, orgId, bearerToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> org) {
                        return new OrganizationSummary(
                                parseUuid(org, "id"),
                                parseUuid(org, "tenantId"),
                                parseString(org, "name"),
                                parseString(org, "code"),
                                parseString(org, "status")
                        );
                    }
                    throw new IllegalStateException("Réponse organization-core inattendue");
                })
                .doOnError(e -> log.error("[org-core] Erreur récupération org={} : {}", orgId, e.getMessage()));
    }

    // ----------------------------------------------------------------
    // Modèles de réponse
    // ----------------------------------------------------------------

    public record OrganizationSummary(
            UUID id,
            UUID tenantId,
            String name,
            String code,
            String status
    ) {}

    // ----------------------------------------------------------------
    // Utilitaires
    // ----------------------------------------------------------------

    private void applyKernelHeaders(HttpHeaders headers, UUID tenantId, UUID orgId, String bearerToken) {
        if (tenantId != null) {
            headers.set("X-Tenant-Id", tenantId.toString());
        }
        if (orgId != null) {
            headers.set("X-Organization-Id", orgId.toString());
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
    }

    private UUID parseUuid(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? UUID.fromString(val.toString()) : null;
    }

    private String parseString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
