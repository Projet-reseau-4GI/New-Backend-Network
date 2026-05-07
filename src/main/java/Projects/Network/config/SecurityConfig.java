package Projects.Network.config;

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
            JwtAuthenticationFilter jwtFilter) {

        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchange -> exchange
                // Public: Swagger
                .pathMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs/**", "/api-docs/**", "/webjars/**"
                ).permitAll()
                // Public: Auth endpoints
                .pathMatchers(
                    "/api/auth/register",
                    "/api/auth/verify-email",
                    "/api/auth/resend-otp",
                    "/api/auth/login",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"
                ).permitAll()
                // Public: OTP legacy endpoints
                .pathMatchers("/api/auth/otp/**").permitAll()
                // Public: Health & metrics (admin-used, can be restricted in prod)
                .pathMatchers("/api/metrics/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                // Public: Document verification (uses API key auth, not JWT)
                .pathMatchers("/api/verify/**", "/api/documents/**").permitAll()
                // Public: Dashboard (restrict per platform via platformId in JWT if needed)
                .pathMatchers("/api/dashboard/**").permitAll()
                // Public: Seeder (disable or restrict in prod)
                .pathMatchers("/api/admin/seed-data").permitAll()
                // Protected: profile, password change, API key regen
                .pathMatchers(
                    "/api/auth/me",
                    "/api/auth/change-password",
                    "/api/auth/regenerate-token/**",
                    "/api/auth/confirm-regenerate/**",
                    "/api/admin/platforms/**"
                ).authenticated()
                .anyExchange().permitAll()
            )
            .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
}