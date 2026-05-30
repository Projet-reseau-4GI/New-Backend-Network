package com.projects.adapter.out.security;

import com.projects.application.port.out.TokenServicePort;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Infrastructure adapter — implements TokenServicePort using JJWT.
 * Replaces the old JwtService.
 */
@Component
@Slf4j
public class JwtTokenAdapter implements TokenServicePort {

    @Value("${jwt.secret:VerifID_Super_Secret_JWT_Key_2025_Must_Be_At_Least_256_Bits_Long!!}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(String email, Long id, String name) {
        return generateToken(email, id, name, null);
    }

    @Override
    public String generateToken(String email, Long id, String name, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);

        JwtBuilder builder = Jwts.builder()
            .setSubject(email)
            .claim("platformId", id)
            .claim("name", name)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (role != null && !role.isBlank()) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    @Override
    public boolean isTokenValid(String token) {
        return parseClaims(token) != null;
    }

    @Override
    public String extractEmail(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    @Override
    public Long extractId(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;
        Object id = claims.get("platformId");
        return id instanceof Number ? ((Number) id).longValue() : null;
    }

    /** Returns Claims or null if invalid/expired. */
    public Claims parseClaims(String token) {
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
}
