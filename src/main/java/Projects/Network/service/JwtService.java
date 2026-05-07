package Projects.Network.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service for generating and validating JWT session tokens.
 * Uses JJWT 0.11.5 API (setSubject / setExpiration style).
 */
@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret:VerifID_Super_Secret_JWT_Key_2025_Must_Be_At_Least_256_Bits_Long!!}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Generate a JWT for a platform (email as subject, platformId as claim). */
    public String generateToken(String email, Long platformId, String name) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(email)
                .claim("platformId", platformId)
                .claim("name", name)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate a JWT token and return the claims.
     * Returns null if the token is invalid or expired.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }

    /** Extract the email (subject) from a JWT token. */
    public String extractEmail(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /** Extract the platform ID from a JWT token. */
    public Long extractPlatformId(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        Object id = claims.get("platformId");
        if (id instanceof Number) return ((Number) id).longValue();
        return null;
    }

    /** Check if a token is still valid (not expired, not malformed). */
    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }
}
