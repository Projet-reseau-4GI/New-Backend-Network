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
import java.util.*;

/**
 * Service responsible for JWT operations: generation, validation, and parsing.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:votreCleSecreteTresLongueEtSecuriseeDe32CaracteresMinimum}")
    private String secret;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Validates the JWT token and extracts the User information.
     * @param token The JWT string from the header
     * @return A Mono emitting the User object if valid
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
     * @param user The user for whom the token is generated
     * @return A string representing the JWT
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // Expire dans 10h
                .signWith(key)
                .compact();
    }
}