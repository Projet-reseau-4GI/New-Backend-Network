package Projects.Network.config;

import Projects.Network.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.util.Collections;

/**
 * Filter that intercepts requests to validate JWT and set the security context.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter implements WebFilter {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            return jwtService.validateAndGetPrincipal(token)
                    .flatMap(user -> chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList())
                            )))
                    .onErrorResume(e -> chain.filter(exchange));
        }
        return chain.filter(exchange);
    }
}