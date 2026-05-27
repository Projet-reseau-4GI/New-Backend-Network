package com.yowyob.flashshop.service;

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
 * Uses JJWT 0.11.5 API for token operations.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret:VerifID_Super_Secret_JWT_Key_2025_Must_Be_At_Least_256_Bits_Long!!}")
    private String jwt_secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwt_expiration_ms;

    private SecretKey getSigningKey() {
        byte[] key_bytes = jwt_secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(key_bytes);
    }

    /**
     * Generates a JWT for a platform.
     * Claims include platformId, name, and role.
     *
     * @param email       the subject of the token (email)
     * @param platform_id the identifier of the platform
     * @param name        the name of the platform
     * @param role        the security role
     * @return a signed JWT string
     */
    public String generateToken(String email, Long platform_id, String name, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwt_expiration_ms);

        return Jwts.builder()
                .setSubject(email)
                .claim("platformId", platform_id)
                .claim("name", name)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extracts the role from a JWT token.
     *
     * @param token the JWT string
     * @return the extracted role
     */
    public String extractRole(String token) {
        Claims claims = validateToken(token);
        return claims != null ? (String) claims.get("role") : null;
    }

    /**
     * Validates a JWT token and returns the claims.
     *
     * @param token the JWT string
     * @return the body of the validated token, or null if invalid
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

    /**
     * Extracts the email (subject) from a JWT token.
     *
     * @param token the JWT string
     * @return the extracted email
     */
    public String extractEmail(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Extracts the platform ID from a JWT token.
     *
     * @param token the JWT string
     * @return the extracted platform ID
     */
    public Long extractPlatformId(String token) {
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        Object id = claims.get("platformId");
        if (id instanceof Number) {
            return ((Number) id).longValue();
        }
        return null;
    }

    /**
     * Checks if a token is still valid.
     *
     * @param token the JWT string
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        return validateToken(token) != null;
    }
}
