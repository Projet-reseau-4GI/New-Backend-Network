package com.projects.config.kernel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration du WebClient dédié aux appels vers le Kernel RT-Comops.
 *
 * Chaque requête sortante porte automatiquement :
 *   - X-Client-Id  : identifiant stable de la ClientApplication VerifID
 *   - X-Api-Key    : secret serveur (jamais exposé au frontend)
 *
 * Les headers dynamiques par requête (X-Tenant-Id, X-Organization-Id,
 * Authorization: Bearer) sont ajoutés dans les services appelants.
 */
@Configuration
public class KernelWebClientConfig {

    public static final String HEADER_CLIENT_ID   = "X-Client-Id";
    public static final String HEADER_API_KEY     = "X-Api-Key";
    public static final String HEADER_TENANT_ID   = "X-Tenant-Id";
    public static final String HEADER_ORG_ID      = "X-Organization-Id";
    public static final String HEADER_AGENCY_ID   = "X-Agency-Id";

    @Bean("kernelWebClient")
    public WebClient kernelWebClient(KernelClientProperties props, WebClient.Builder builder) {
        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HEADER_CLIENT_ID, props.getClientId())
                .defaultHeader(HEADER_API_KEY, props.getApiKey())
                .build();
    }
}
