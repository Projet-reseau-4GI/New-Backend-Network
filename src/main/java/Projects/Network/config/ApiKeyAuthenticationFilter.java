package Projects.Network.config;

import Projects.Network.repository.PlatformRepository;
import Projects.Network.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A WebFlux Filter that validates the API Key for Kernel endpoints.
 * It identifies the tenant (Platform) making the request.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-KEY";
    
    private final PlatformRepository platformRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Intercept only paths targeting the kernel, documents functionality,
        // which are meant for the Tenanted APIs and require a platform ID.
        // Paths starting with /api/metrics/ are permitted by SecurityConfig for public access.
        if (!path.startsWith("/api/kernel/") && 
            !path.startsWith("/api/documents/")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey != null) {
            apiKey = apiKey.trim();
        }

        if (apiKey == null || apiKey.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String hashedApiKey = SecurityUtils.hashApiKey(apiKey);

        return platformRepository.findByApiKey(hashedApiKey)
                // Ensure platform exists and is active
                .filter(platform -> Boolean.TRUE.equals(platform.getActive()))
                .flatMap(platform -> 
                     // Valid API KEY: propagate down the chain with updated Context
                     chain.filter(exchange)
                          .contextWrite(ctx -> ReactiveTenantContext.putPlatform(ctx, platform))
                )
                .switchIfEmpty(Mono.defer(() -> {
                    // Invalid/Inactive API Key
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
    }
}
