package com.projects.config;

import com.projects.adapter.out.kernel.JwksTokenValidator;
import com.projects.adapter.out.kernel.KernelTokenClaims;
import com.projects.application.port.out.TokenServicePort;
import com.projects.config.kernel.KernelClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Filtre d'authentification JWT réactif — double mode.
 *
 * Mode RS256 (kernel.jwt.rs256-enabled=true) :
 *   Valide le JWT RS256 émis par le kernel auth-core via JWKS.
 *   Construit le principal depuis les claims kernel (userId, tenantId, email).
 *
 * Mode HS256 local (kernel.jwt.rs256-enabled=false, défaut) :
 *   Valide le JWT HS256 local généré par JwtTokenAdapter.
 *   Maintenu pour la rétrocompatibilité pendant la migration.
 *
 * Les deux modes injectent un UsernamePasswordAuthenticationToken dans le
 * SecurityContext Spring.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private final TokenServicePort tokenService;
    private final KernelClientProperties kernelProperties;
    private final JwksTokenValidator jwksTokenValidator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (kernelProperties.getJwt().isRs256Enabled()) {
            return handleRs256(token, exchange, chain);
        }

        return handleHs256Local(token, exchange, chain);
    }

    // ----------------------------------------------------------------
    // Mode RS256 — validation via JWKS du kernel
    // ----------------------------------------------------------------

    private Mono<Void> handleRs256(String token, ServerWebExchange exchange, WebFilterChain chain) {
        return jwksTokenValidator.validateAndExtract(token)
                .flatMap(claims -> {
                    UsernamePasswordAuthenticationToken auth = buildAuthFromKernelClaims(claims);
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                            .contextWrite(ctx -> ReactiveTenantContext.putKernelClaims(ctx, claims));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[JWT RS256] Token invalide ou non RS256 — passage au fallback HS256");
                    return handleHs256Local(token, exchange, chain);
                }));
    }

    private UsernamePasswordAuthenticationToken buildAuthFromKernelClaims(KernelTokenClaims claims) {
        String principal = claims.email() != null ? claims.email()
                : (claims.subject() != null ? claims.subject() : "kernel-user");
        return new UsernamePasswordAuthenticationToken(
                principal,
                claims.userId(),
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM"),
                        new SimpleGrantedAuthority("ROLE_KERNEL_USER"))
        );
    }

    // ----------------------------------------------------------------
    // Mode HS256 local — rétrocompatibilité
    // ----------------------------------------------------------------

    private Mono<Void> handleHs256Local(String token, ServerWebExchange exchange, WebFilterChain chain) {
        boolean isValid = tokenService.isTokenValid(token);
        if (!isValid) {
            log.debug("[JWT HS256] Token invalide ou expiré — passage sans authentification");
            return chain.filter(exchange);
        }

        String email      = tokenService.extractEmail(token);
        Long   platformId = tokenService.extractId(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        platformId,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM"))
                );

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
