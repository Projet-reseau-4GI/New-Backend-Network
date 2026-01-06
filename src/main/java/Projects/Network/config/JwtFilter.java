package Projects.Network.config;

import Projects.Network.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT authentication filter for WebFlux applications.
 *
 * This filter is responsible for intercepting all incoming HTTP requests in the reactive WebFlux pipeline
 * and performing JWT-based authentication. It extracts JWT tokens from the Authorization header,
 * validates them using the JwtService, and adds authenticated user information to the reactive context.
 *
 * The filter implements a whitelist approach for public endpoints that do not require authentication,
 * such as login, registration, and health check endpoints. For all other endpoints, the presence
 * and validity of a JWT token is verified before allowing the request to proceed.
 *
 * Key responsibilities:
 * - Extract JWT tokens from the "Authorization: Bearer <token>" header format
 * - Validate tokens using the injected JwtService
 * - Add authenticated user information to the reactive context for downstream filters and controllers
 * - Bypass authentication for explicitly whitelisted public endpoints
 * - Allow requests without tokens to proceed (authorization logic is handled separately)
 *
 * Technical considerations:
 * - This filter operates in the reactive WebFlux pipeline using Mono and reactive context
 * - The authenticated user object is stored in the reactive context with key "user"
 * - If token validation fails, the request proceeds without user context (fail-open approach)
 * - The filter uses constructor injection via Lombok's @RequiredArgsConstructor
 *
 * Security note: This filter only performs authentication (identifying the user).
 * Authorization (determining if the user has permission) should be handled by
 * downstream security filters or controller-level annotations.
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@RequiredArgsConstructor
public class JwtFilter implements WebFilter {

    // Injected dependency for JWT token operations (validation, parsing, user extraction)
    private final JwtService jwtService;

    // HTTP header constants for improved maintainability and avoiding magic strings
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = 7;

    // Public endpoint path prefixes that should bypass authentication
    private static final String AUTH_ENDPOINT_PREFIX = "/api/auth/";
    private static final String HEALTH_ENDPOINT = "/health";

    /**
     * Core filter method that intercepts every HTTP request in the WebFlux pipeline.
     *
     * This method implements the authentication logic by:
     * 1. Checking if the requested path is a public endpoint that should bypass authentication
     * 2. Extracting the JWT token from the Authorization header if present
     * 3. Validating the token and extracting user information using JwtService
     * 4. Adding the authenticated user to the reactive context for downstream components
     * 5. Allowing the request to proceed through the filter chain regardless of authentication status
     *
     * Flow control:
     * - If endpoint is public → immediately proceed to next filter
     * - If Authorization header is missing → proceed to next filter without user context
     * - If Authorization header is present but invalid format → proceed without user context
     * - If token validation succeeds → add user to context and proceed
     * - If token validation fails → proceed without user context (fail-open)
     *
     * The fail-open approach means requests are not rejected at this level if authentication fails.
     * Actual authorization and access control should be enforced by downstream security mechanisms.
     *
     * @param exchange the current ServerWebExchange containing the HTTP request and response,
     *                 provides access to request attributes, headers, path, and reactive context
     * @param chain the WebFilterChain to delegate to the next filter in the pipeline,
     *              must be invoked to continue processing the request
     * @return a Mono<Void> representing the asynchronous completion of the filter execution,
     *         completes when this filter and all downstream filters have finished processing
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Extract the request path to determine if authentication should be bypassed
        String path = exchange.getRequest().getPath().value();

        // Check if this is a public endpoint that doesn't require authentication
        // Public endpoints include authentication routes and health checks
        if (isPublicEndpoint(path)) {
            // Bypass authentication and proceed directly to the next filter in the chain
            return chain.filter(exchange);
        }

        // Attempt to extract the Authorization header from the incoming request
        // The Authorization header should contain the JWT token in "Bearer <token>" format
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        // Check if Authorization header exists and follows the "Bearer <token>" format
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            // Extract the actual JWT token by removing the "Bearer " prefix
            String token = extractToken(authHeader);

            // Validate the token and extract user information asynchronously
            // This returns a Mono that emits the authenticated user if validation succeeds
            return jwtService.validateAndGetPrincipal(token)
                    .flatMap(user -> {
                        // Token validation succeeded, add user to reactive context
                        // The user object will be available to all downstream filters and controllers
                        // via the reactive context with key "user"
                        return chain.filter(exchange)
                                .contextWrite(ctx -> ctx.put("user", user));
                    })
                    .switchIfEmpty(
                            // Token validation failed or returned empty result
                            // Proceed with the request without user context (fail-open approach)
                            chain.filter(exchange)
                    );
        }

        // No Authorization header present or invalid format
        // Proceed with the request without attempting authentication
        return chain.filter(exchange);
    }

    /**
     * Determines if a given request path should bypass JWT authentication.
     *
     * This method implements the whitelist logic for public endpoints that should be
     * accessible without requiring a valid JWT token. This includes authentication
     * endpoints (login, register) where users cannot yet have a token, as well as
     * health check endpoints used by monitoring systems.
     *
     * Current whitelist:
     * - /api/auth/* - All authentication-related endpoints (login, register, password reset, etc.)
     * - /health - Application health check endpoint for monitoring and load balancers
     *
     * Design considerations:
     * - Uses prefix matching for /api/auth/ to cover all authentication sub-routes
     * - Uses exact matching for /health endpoint
     * - Can be extended to include additional public endpoints as needed
     * - Changes to public endpoints should be carefully reviewed for security implications
     *
     * @param path the request path extracted from ServerWebExchange, should be the full path
     *             without query parameters (e.g., "/api/auth/login")
     * @return true if the path is a public endpoint that should skip authentication,
     *         false if the path requires JWT authentication to be attempted
     */
    private boolean isPublicEndpoint(String path) {
        // Check if path starts with authentication endpoint prefix or exactly matches health endpoint
        return path.startsWith(AUTH_ENDPOINT_PREFIX) || path.equals(HEALTH_ENDPOINT);
    }

    /**
     * Extracts the JWT token from the Authorization header value.
     *
     * JWT tokens in HTTP requests follow the "Bearer Token" authentication scheme
     * as defined in RFC 6750. The Authorization header format is:
     * "Authorization: Bearer <jwt-token>"
     *
     * This method removes the "Bearer " prefix (7 characters) to extract just the
     * actual JWT token string, which consists of three Base64-encoded parts
     * separated by dots: header.payload.signature
     *
     * Precondition: The caller must verify that the header starts with "Bearer "
     * before calling this method to avoid StringIndexOutOfBoundsException.
     *
     * Example:
     * Input:  "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
     * Output: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
     *
     * @param authHeader the full Authorization header value starting with "Bearer "
     *                   (e.g., "Bearer eyJhbGciOiJIUzI1...")
     * @return the JWT token string without the "Bearer " prefix, ready for validation
     *         and parsing by the JwtService
     */
    private String extractToken(String authHeader) {
        // Remove the first 7 characters ("Bearer ") to get the raw JWT token
        return authHeader.substring(BEARER_PREFIX_LENGTH);
    }
}