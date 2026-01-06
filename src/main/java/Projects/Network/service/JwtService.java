package Projects.Network.service;

import Projects.Network.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JwtService
 *
 * Service responsible for managing JSON Web Token (JWT) operations.
 *
 * This service provides functionalities for:
 * - Initializing the cryptographic signing key
 * - Generating JWT tokens for authenticated users
 * - Validating incoming JWT tokens
 * - Extracting user-related information from valid tokens
 *
 * The service is used mainly in authentication and authorization
 * workflows to ensure secure access to protected resources.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Service
public class JwtService {

    /**
     * Secret key used to sign and verify JWT tokens.
     *
     * The secret must be sufficiently long and secure to ensure
     * the integrity and authenticity of generated tokens.
     */
    @Value("${jwt.secret:votreCleSecreteTresLongueEtSecuriseeDe32CaracteresMinimum}")
    private String secret;

    /**
     * Cryptographic key derived from the configured secret.
     */
    private Key key;

    /**
     * Initializes the signing key after dependency injection.
     *
     * This method is executed once at application startup and
     * prepares the key used for all JWT operations.
     */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Validates a JWT token and extracts the authenticated user information.
     *
     * This method parses the token, verifies its signature, and retrieves
     * claims embedded within the token.
     *
     * If the token is invalid, expired, or cannot be parsed, an empty Mono
     * is returned.
     *
     * @param token the JWT token extracted from the HTTP Authorization header
     * @return a Mono emitting a User object if the token is valid
     */
    public Mono<User> validateAndGetPrincipal(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            User user = new User();

            Object userIdClaim = claims.get("userId");
            if (userIdClaim != null) {
                user.setUserId(UUID.fromString(userIdClaim.toString()));
            }

            user.setEmail(claims.getSubject());
            return Mono.just(user);

        } catch (Exception e) {
            return Mono.empty();
        }
    }

    /**
     * Generates a JWT token for a given user.
     *
     * The token contains:
     * - The user identifier as a custom claim
     * - The user's email as the subject
     * - Issue and expiration timestamps
     *
     * The generated token is signed using the configured cryptographic key.
     *
     * @param user the authenticated user
     * @return a signed JWT token as a String
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(
                        new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)
                )
                .signWith(key)
                .compact();
    }
}
