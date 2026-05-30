package com.projects.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.url:http://localhost:8080}")
    private String serverUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("VerifID – API de vérification de documents")
                .version("2.0")
                .description(
                    "Plateforme de vérification de pièces d'identité camerounaises. " +
                    "Utiliser le token JWT (obtenu via `POST /api/auth/login`) pour les routes protégées. " +
                    "Utiliser la clé API pour les routes de vérification de documents."
                )
                .contact(new Contact()
                    .name("VerifID Support")
                    .email("support@network.com")
                    .url("https://github.com/")))
            .servers(List.of(
                new Server().url(serverUrl).description("Serveur principal"),
                new Server().url("http://localhost:8080").description("Développement local")
            ))
            // JWT Bearer Auth scheme
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT de session. Obtenu via POST /api/auth/login")
                )
            );
    }
}