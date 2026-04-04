package Projects.Network.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security configuration for reactive WebFlux application.
 *
 * This configuration class sets up the security infrastructure for the entire
 * application,
 * defining how HTTP requests should be secured, which authentication mechanisms
 * to use,
 * and how passwords should be encoded. It uses Spring Security's reactive
 * support designed
 * specifically for WebFlux applications that use non-blocking, reactive
 * programming models.
 *
 * Current security configuration:
 * - CSRF protection is disabled (suitable for stateless REST APIs using JWT
 * tokens)
 * - All HTTP exchanges are permitted without authentication (open access)
 * - BCrypt password encoding for secure password storage
 *
 * IMPORTANT SECURITY NOTICE:
 * This configuration currently permits all requests without authentication,
 * which is
 * appropriate for development and testing phases. However, for production
 * deployment,
 * this configuration MUST be enhanced to include:
 * - Proper authentication rules based on JWT tokens
 * - Role-based access control (RBAC) for different user types
 * - Rate limiting to prevent brute force attacks
 * - HTTPS enforcement for secure data transmission
 * - CSRF protection for stateful operations if needed
 * - Security headers (X-Frame-Options, X-Content-Type-Options, etc.)
 *
 * Architecture context:
 * This configuration works in conjunction with:
 * - JwtFilter: Extracts and validates JWT tokens from requests
 * - AuthService: Handles user authentication and token generation
 * - PasswordEncoder: Securely hashes passwords before database storage
 *
 * The reactive security model used here differs from traditional servlet-based
 * security
 * in that it operates on reactive streams (Mono and Flux) and uses
 * ServerWebExchange
 * instead of HttpServletRequest/Response.
 *
 * Migration path to production:
 * When moving to production, update the securityWebFilterChain method to
 * include:
 * 1. Path-based authorization rules (e.g., /api/admin/** requires ADMIN role)
 * 2. JWT authentication filter integration
 * 3. Exception handling for authentication failures
 * 4. CORS configuration for cross-origin requests if needed
 * 5. Session management strategy (stateless for JWT-based auth)
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for handling HTTP requests.
     *
     * The SecurityWebFilterChain is the reactive equivalent of the traditional
     * servlet-based
     * FilterChain. It defines a series of security filters that process incoming
     * HTTP requests
     * before they reach the application controllers. Each filter can inspect,
     * modify, or
     * reject requests based on security policies.
     *
     * Current configuration decisions and rationale:
     *
     * 1. CSRF (Cross-Site Request Forgery) Protection - DISABLED
     * Rationale: This application uses JWT tokens for authentication, which are
     * sent
     * in the Authorization header rather than cookies. CSRF attacks specifically
     * target
     * cookie-based authentication by tricking browsers into sending cookies
     * automatically.
     * Since JWTs in headers require explicit JavaScript code to include them, CSRF
     * protection is unnecessary and would add complexity without security benefit.
     *
     * Technical detail: CSRF tokens would need to be generated and validated for
     * each
     * state-changing request, but JWT authentication already provides request
     * authenticity
     * through cryptographic signatures.
     *
     * 2. Authorization Rules - PERMIT ALL
     * Current state: All requests are allowed without authentication checks.
     * This is appropriate for:
     * - Development phase where rapid testing is needed
     * - Public APIs that don't require authentication
     * - Demonstration or prototype applications
     *
     * PRODUCTION REQUIREMENT:
     * This MUST be changed before production deployment. Replace with proper rules
     * like:
     *
     * .authorizeExchange(exchange -> exchange
     * .pathMatchers("/api/auth/**").permitAll() // Public authentication endpoints
     * .pathMatchers("/health", "/metrics").permitAll() // Monitoring endpoints
     * .pathMatchers("/api/admin/**").hasRole("ADMIN") // Admin-only endpoints
     * .pathMatchers("/api/documents/**").authenticated() // Require valid JWT
     * .anyExchange().denyByDefault() // Deny anything not explicitly allowed
     * )
     *
     * Filter chain execution order:
     * When a request arrives, it passes through filters in this order:
     * 1. CSRF filter (currently disabled)
     * 2. Authorization filter (currently permits all)
     * 3. Authentication filters (JwtFilter should be integrated here)
     * 4. Exception translation filters
     * 5. Finally reaches the application controllers
     *
     * Integration with JwtFilter:
     * The JwtFilter (defined in JwtFilter.java) currently operates independently.
     * For production, it should be integrated into this filter chain using:
     * .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
     *
     * Performance considerations:
     * The reactive filter chain is non-blocking and can handle high concurrency.
     * Each filter returns a Mono<Void> that completes when processing is done,
     * allowing the reactor to efficiently schedule other work while waiting.
     *
     * Error handling:
     * Authentication failures should result in 401 Unauthorized responses.
     * Authorization failures should result in 403 Forbidden responses.
     * These can be configured using exceptionHandling() in the filter chain.
     *
     * @param http the ServerHttpSecurity builder for configuring security rules,
     *             provides a fluent API for defining security policies specific to
     *             reactive applications using ServerWebExchange instead of
     *             HttpServletRequest
     * @return the configured SecurityWebFilterChain that will process all HTTP
     *         requests
     *         through the defined security filters before reaching application
     *         controllers
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/metrics/**").permitAll()
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**",
                                "/webjars/**")
                        .permitAll()
                        .anyExchange().permitAll())
                .build();

    }

    /**
     * Provides a BCrypt password encoder bean for secure password hashing.
     *
     * Password security is critical for protecting user accounts. This method
     * creates
     * a BCryptPasswordEncoder that implements industry-standard password hashing
     * using
     * the BCrypt algorithm. BCrypt is specifically designed for password hashing
     * and
     * provides several security advantages over simple hashing algorithms like MD5
     * or SHA.
     *
     * Why BCrypt is used:
     *
     * 1. Adaptive hashing with configurable work factor:
     * BCrypt includes a work factor (cost parameter) that determines how many
     * rounds
     * of hashing are performed. As computers get faster, the work factor can be
     * increased
     * to maintain security without changing the algorithm. The default work factor
     * is 10,
     * which means 2^10 = 1024 rounds of hashing.
     *
     * 2. Built-in salt generation:
     * BCrypt automatically generates a unique random salt for each password hash.
     * The salt is stored as part of the hash output, eliminating the need to manage
     * salts separately. This prevents rainbow table attacks where pre-computed
     * hashes
     * could be used to crack passwords.
     *
     * Salt format: Salts are 16 bytes (128 bits) of cryptographically secure random
     * data,
     * which provides 2^128 possible salts, making pre-computation attacks
     * infeasible.
     *
     * 3. Deliberately slow:
     * Unlike algorithms designed for speed (like SHA), BCrypt is intentionally
     * slow.
     * This makes brute-force attacks computationally expensive. A single password
     * verification takes approximately 100ms with default settings, which is
     * imperceptible to legitimate users but makes cracking millions of passwords
     * prohibitively time-consuming for attackers.
     *
     * 4. Resistance to hardware acceleration:
     * BCrypt's algorithm is designed to be memory-hard and resistant to
     * parallelization
     * on GPUs or specialized hardware (ASICs), unlike algorithms like SHA which can
     * be
     * computed very quickly on GPUs.
     *
     * BCrypt hash output format:
     * $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     *
     * Breaking down this format:
     * - $2a$ : BCrypt algorithm version identifier
     * - 10$ : Work factor (cost parameter), determines computation time
     * - N9qo... : 22-character Base64-encoded salt (128 bits of random data)
     * - ...lhWy : 31-character Base64-encoded hash result
     *
     * Total length is 60 characters, which is why database password columns should
     * be
     * VARCHAR(60) or larger to accommodate the full hash string.
     *
     * Usage in the application:
     *
     * Encoding (during user registration):
     * String plainPassword = "user_input_password";
     * String hashedPassword = passwordEncoder.encode(plainPassword);
     * // Store hashedPassword in database
     *
     * Verification (during login):
     * String plainPassword = "user_input_password";
     * String storedHash = // Retrieved from database
     * boolean matches = passwordEncoder.matches(plainPassword, storedHash);
     * // Allow login if matches is true
     *
     * Security best practices implemented:
     * - Never store passwords in plain text or reversible encryption
     * - Use a unique salt for each password (handled automatically by BCrypt)
     * - Use a strong, adaptive hashing algorithm (BCrypt)
     * - Set appropriate work factor balancing security and performance
     *
     * Performance considerations:
     * BCrypt verification is CPU-intensive by design. For high-traffic
     * applications:
     * - Implement rate limiting on login endpoints to prevent brute force
     * - Consider caching successful authentication results with short TTL
     * - Monitor CPU usage during peak authentication times
     * - Scale horizontally if authentication becomes a bottleneck
     *
     * Future security enhancements:
     * - Consider increasing work factor as hardware improves (currently 10)
     * - Implement password strength requirements (length, complexity)
     * - Add password history to prevent reuse of old passwords
     * - Implement account lockout after multiple failed attempts
     * - Consider multi-factor authentication for sensitive operations
     *
     * Compliance considerations:
     * This password encoding strategy helps meet requirements from:
     * - OWASP Password Storage Cheat Sheet
     * - NIST Digital Identity Guidelines (SP 800-63B)
     * - PCI DSS Requirement 8.2.1 (strong cryptography for password protection)
     * - GDPR security requirements for personal data protection
     *
     * @return a BCryptPasswordEncoder instance configured with default settings
     *         (work factor 10, SecureRandom for salt generation) that can be
     *         injected
     *         into any service requiring password hashing or verification
     *         operations
     */
}