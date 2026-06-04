package com.projects.adapter.out.kernel;

import com.projects.config.kernel.KernelClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptateur HTTP vers le kernel-core et l'auth-core du Kernel RT-Comops.
 *
 * Responsabilités :
 *  1. Enregistrer VerifID comme ClientApplication dans le kernel (bootstrap initial).
 *  2. Authentifier un utilisateur via l'auth-core (login tenant-scopé).
 *  3. Découvrir les contextes de login disponibles pour un email.
 *
 * Endpoints utilisés :
 *   POST /api/kernel/client-applications   → enregistrement ClientApplication
 *   POST /api/auth/login                   → authentification utilisateur
 *   POST /api/auth/discover-login-contexts → découverte des contextes de login
 */
@Service
@Slf4j
public class KernelAuthService {

    private static final String CLIENT_APP_ENDPOINT    = "/api/kernel/client-applications";
    private static final String AUTH_LOGIN_ENDPOINT    = "/api/auth/login";
    private static final String AUTH_DISCOVER_ENDPOINT = "/api/auth/discover-login-contexts";

    private final WebClient kernelWebClient;
    private final KernelClientProperties kernelProperties;

    public KernelAuthService(
            @Qualifier("kernelWebClient") WebClient kernelWebClient,
            KernelClientProperties kernelProperties) {
        this.kernelWebClient = kernelWebClient;
        this.kernelProperties = kernelProperties;
    }

    /**
     * Enregistre VerifID comme ClientApplication dans le kernel.
     * À appeler une seule fois lors du bootstrap (via admin ou script d'init).
     *
     * @param adminBearerToken Token JWT d'un administrateur kernel
     * @param tenantId         UUID du tenant cible
     * @return Réponse du kernel avec clientId et secret
     */
    public Mono<ClientApplicationRegistrationResult> registerClientApplication(
            String adminBearerToken,
            UUID tenantId) {

        Map<String, Object> body = Map.of(
                "clientId", kernelProperties.getClientId(),
                "name", "VerifID Backend",
                "description", "Service de validation de documents d'identité",
                "allowedServiceCodes", Set.of(kernelProperties.getServiceCode()),
                "systemManaged", false
        );

        return kernelWebClient.post()
                .uri(CLIENT_APP_ENDPOINT)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    if (tenantId != null) h.set("X-Tenant-Id", tenantId.toString());
                    if (adminBearerToken != null) h.setBearerAuth(adminBearerToken);
                })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> d) {
                        return new ClientApplicationRegistrationResult(
                                parseString(d, "clientId"),
                                parseString(d, "plainSecret"),
                                parseString(d, "status")
                        );
                    }
                    throw new IllegalStateException("Réponse kernel inattendue lors de l'enregistrement");
                })
                .doOnSuccess(r -> log.info("[kernel-core] ClientApplication enregistrée — clientId={}", r.clientId()))
                .doOnError(e -> log.error("[kernel-core] Erreur enregistrement ClientApplication : {}", e.getMessage()));
    }

    /**
     * Authentifie un utilisateur via l'auth-core du kernel.
     * Utilisé quand VerifID délègue l'authentification au kernel.
     *
     * @param tenantId  UUID du tenant
     * @param principal Email ou username
     * @param password  Mot de passe en clair
     * @return JWT RS256 et informations du compte utilisateur
     */
    public Mono<AuthLoginResult> loginUser(UUID tenantId, String principal, String password) {
        Map<String, Object> body = Map.of(
                "tenantId", tenantId.toString(),
                "principal", principal,
                "password", password
        );

        return kernelWebClient.post()
                .uri(AUTH_LOGIN_ENDPOINT)
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.set("X-Tenant-Id", tenantId.toString());
                })
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> d) {
                        return new AuthLoginResult(
                                parseString(d, "accessToken"),
                                parseString(d, "refreshToken"),
                                parseUuid(d, "userId"),
                                parseString(d, "email"),
                                parseString(d, "status")
                        );
                    }
                    throw new IllegalStateException("Réponse auth-core inattendue");
                })
                .doOnError(e -> log.error("[auth-core] Erreur login utilisateur : {}", e.getMessage()));
    }

    /**
     * Découvre les contextes de login disponibles pour un email donné.
     * Correspond à DS-AU-07 du rapport.
     */
    public Mono<Map<String, Object>> discoverLoginContexts(String email) {
        Map<String, Object> body = Map.of("email", email);

        return kernelWebClient.post()
                .uri(AUTH_DISCOVER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnError(e -> log.error("[auth-core] Erreur découverte contextes : {}", e.getMessage()));
    }

    // ----------------------------------------------------------------
    // Types de réponse
    // ----------------------------------------------------------------

    public record ClientApplicationRegistrationResult(
            String clientId,
            String plainSecret,
            String status
    ) {}

    public record AuthLoginResult(
            String accessToken,
            String refreshToken,
            UUID userId,
            String email,
            String status
    ) {}

    // ----------------------------------------------------------------
    // Utilitaires
    // ----------------------------------------------------------------

    private UUID parseUuid(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? UUID.fromString(val.toString()) : null;
    }

    private String parseString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
