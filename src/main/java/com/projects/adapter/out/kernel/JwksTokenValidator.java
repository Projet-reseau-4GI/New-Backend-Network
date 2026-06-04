package com.projects.adapter.out.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projects.config.kernel.KernelClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validateur de JWT RS256 via le JWKS exposé par le kernel-core.
 *
 * Endpoint kernel : GET /.well-known/jwks.json
 *
 * Ce composant :
 *  1. Récupère le jeu de clés publiques (JWKS) du kernel au démarrage et le met en cache.
 *  2. Valide la signature RS256 du JWT porteur.
 *  3. Extrait les claims métier (tenantId, organizationId, userId, actorId).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwksTokenValidator {

    private final KernelClientProperties kernelProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /** Cache des clés publiques RSA indexées par kid */
    private final ConcurrentHashMap<String, PublicKey> keyCache = new ConcurrentHashMap<>();

    /**
     * Récupère le JWKS depuis le kernel et met à jour le cache local.
     * Appelé de façon réactive à la demande ou au démarrage.
     */
    public Mono<Void> refreshJwks() {
        return webClientBuilder.build()
                .get()
                .uri(kernelProperties.getJwksUri())
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(jwks -> {
                    try {
                        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
                        if (keys == null) {
                            log.warn("[JWKS] Aucune clé dans la réponse du kernel");
                            return;
                        }
                        keyCache.clear();
                        for (Map<String, Object> key : keys) {
                            String kid = (String) key.get("kid");
                            String kty = (String) key.get("kty");
                            if (!"RSA".equals(kty)) continue;
                            PublicKey publicKey = buildRsaPublicKey(
                                    (String) key.get("n"),
                                    (String) key.get("e"));
                            if (kid != null && publicKey != null) {
                                keyCache.put(kid, publicKey);
                                log.info("[JWKS] Clé publique RSA chargée — kid={}", kid);
                            }
                        }
                    } catch (Exception e) {
                        log.error("[JWKS] Erreur lors du parsing des clés : {}", e.getMessage());
                    }
                })
                .then();
    }

    /**
     * Valide un token JWT RS256 et retourne ses claims si valide.
     * Retourne Mono.empty() si le token est invalide ou que la clé est inconnue.
     */
    public Mono<KernelTokenClaims> validateAndExtract(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Mono.empty();
        }

        // Décode le header du JWT pour récupérer le kid
        try {
            String[] parts = bearerToken.split("\\.");
            if (parts.length != 3) return Mono.empty();

            String headerJson = new String(Base64.getUrlDecoder().decode(pad(parts[0])));
            Map<?, ?> header = objectMapper.readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");
            String alg = (String) header.get("alg");

            if (!"RS256".equals(alg)) {
                log.debug("[JWKS] Algorithme non RS256 ignoré : {}", alg);
                return Mono.empty();
            }

            // Cherche la clé en cache, sinon rafraîchit le JWKS
            PublicKey key = keyCache.get(kid);
            if (key == null) {
                return refreshJwks().then(Mono.defer(() -> {
                    PublicKey refreshedKey = keyCache.get(kid);
                    if (refreshedKey == null) {
                        log.warn("[JWKS] Clé introuvable après refresh — kid={}", kid);
                        return Mono.empty();
                    }
                    return verifyClaims(parts, bearerToken, refreshedKey);
                }));
            }

            return verifyClaims(parts, bearerToken, key);

        } catch (Exception e) {
            log.debug("[JWKS] Erreur de validation du token : {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<KernelTokenClaims> verifyClaims(String[] parts, String token, PublicKey publicKey) {
        try {
            // Vérification de signature RSA-SHA256
            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] signature = Base64.getUrlDecoder().decode(pad(parts[2]));

            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signedContent);

            if (!sig.verify(signature)) {
                log.warn("[JWKS] Signature JWT invalide");
                return Mono.empty();
            }

            // Extraction des claims
            String payloadJson = new String(Base64.getUrlDecoder().decode(pad(parts[1])));
            Map<?, ?> payload = objectMapper.readValue(payloadJson, Map.class);

            // Vérification d'expiration
            Object exp = payload.get("exp");
            if (exp instanceof Number expNum) {
                long expEpoch = expNum.longValue();
                if (expEpoch < System.currentTimeMillis() / 1000) {
                    log.debug("[JWKS] Token expiré");
                    return Mono.empty();
                }
            }

            return Mono.just(KernelTokenClaims.fromPayload(payload));

        } catch (Exception e) {
            log.warn("[JWKS] Erreur lors de la vérification RS256 : {}", e.getMessage());
            return Mono.empty();
        }
    }

    private PublicKey buildRsaPublicKey(String n, String e) {
        try {
            BigInteger modulus  = new BigInteger(1, Base64.getUrlDecoder().decode(pad(n)));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(pad(e)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception ex) {
            log.error("[JWKS] Impossible de construire la clé RSA : {}", ex.getMessage());
            return null;
        }
    }

    /** Ajoute le padding Base64 manquant */
    private String pad(String base64url) {
        int mod = base64url.length() % 4;
        if (mod == 2) return base64url + "==";
        if (mod == 3) return base64url + "=";
        return base64url;
    }
}
