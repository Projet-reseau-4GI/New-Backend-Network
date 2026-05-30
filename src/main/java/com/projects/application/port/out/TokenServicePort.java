package com.projects.application.port.out;

/**
 * Outbound port — JWT token generation and validation contract.
 */
public interface TokenServicePort {
    String generateToken(String email, Long id, String name);
    String generateToken(String email, Long id, String name, String role);
    boolean isTokenValid(String token);
    String extractEmail(String token);
    Long extractId(String token);
}
