package com.projects.config;

import com.projects.config.kernel.KernelTenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            KernelTenantContextFilter kernelTenantContextFilter) {

        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchange -> exchange
                // Public: Swagger
                .pathMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs/**", "/api-docs/**", "/webjars/**"
                ).permitAll()
                // Public: Auth endpoints locaux VerifID
                .pathMatchers(
                    "/api/auth/register",
                    "/api/auth/verify-email",
                    "/api/auth/resend-otp",
                    "/api/auth/login",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"
                ).permitAll()
                // Public: OTP legacy
                .pathMatchers("/api/auth/otp/**").permitAll()
                // Public: Health & métriques
                .pathMatchers("/api/metrics/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                // Public: Vérification de documents (auth par X-API-KEY ou JWT kernel)
                .pathMatchers("/api/verify/**", "/api/documents/**").permitAll()
                // Public: Dashboard
                .pathMatchers("/api/dashboard/**").permitAll()
                // Public: Seeder (à restreindre en prod)
                .pathMatchers("/api/admin/seed-data").permitAll()
                // Protected: profil, changement de mot de passe, régénération token
                .pathMatchers(
                    "/api/auth/me",
                    "/api/auth/change-password",
                    "/api/auth/regenerate-token/**",
                    "/api/auth/confirm-regenerate/**",
                    "/api/admin/platforms/**"
                ).authenticated()
                .anyExchange().permitAll()
            )
            /*
             * Ordre des filtres :
             *  1. KernelTenantContextFilter  — lit X-Tenant-Id, X-Org-Id et valide le JWT RS256
             *     via JWKS du kernel. Injecte KernelTokenClaims dans le contexte réactif.
             *  2. JwtAuthenticationFilter    — valide le JWT HS256 local (rétrocompatibilité).
             *     Sera progressivement remplacé par RS256 quand kernel.jwt.rs256-enabled=true.
             */
            .addFilterBefore(kernelTenantContextFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
}