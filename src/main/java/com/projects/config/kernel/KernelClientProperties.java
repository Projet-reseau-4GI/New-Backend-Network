package com.projects.config.kernel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés de connexion au Kernel RT-Comops (KSM Kernel Layer).
 *
 * Injectées depuis application.properties via le préfixe "kernel".
 */
@Component
@ConfigurationProperties(prefix = "kernel")
public class KernelClientProperties {

    /** URL de base du Kernel (ex: http://localhost:8082) */
    private String baseUrl = "http://localhost:8082";

    /** clientId stable de la ClientApplication VerifID dans le Kernel */
    private String clientId = "verifid-backend";

    /** Secret serveur de la ClientApplication (jamais exposé au frontend) */
    private String apiKey = "";

    /** Code de service déclaré dans le Kernel pour VerifID */
    private String serviceCode = "KYC";

    /** URI JWKS du kernel pour valider les JWT RS256 */
    private String jwksUri;

    private Jwt jwt = new Jwt();

    public static class Jwt {
        /** Si true, valide les tokens via RS256/JWKS du kernel au lieu de HS256 local */
        private boolean rs256Enabled = false;

        public boolean isRs256Enabled() { return rs256Enabled; }
        public void setRs256Enabled(boolean rs256Enabled) { this.rs256Enabled = rs256Enabled; }
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getJwksUri() {
        if (jwksUri == null || jwksUri.isBlank()) {
            return baseUrl + "/.well-known/jwks.json";
        }
        return jwksUri;
    }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
}
