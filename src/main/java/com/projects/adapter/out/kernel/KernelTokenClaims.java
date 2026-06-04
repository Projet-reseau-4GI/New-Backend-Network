package com.projects.adapter.out.kernel;

import java.util.Map;
import java.util.UUID;

/**
 * Claims métier extraits d'un JWT RS256 émis par le kernel-core (auth-core).
 *
 * Claims standard du kernel :
 *   sub  → userId (UUID du UserAccount)
 *   tid  → tenantId
 *   oid  → organizationId
 *   aid  → agencyId
 *   act  → actorId
 */
public record KernelTokenClaims(
        UUID userId,
        UUID tenantId,
        UUID organizationId,
        UUID agencyId,
        UUID actorId,
        String subject,
        String email
) {

    public static KernelTokenClaims fromPayload(Map<?, ?> payload) {
        return new KernelTokenClaims(
                parseUuid(payload, "sub"),
                parseUuid(payload, "tid"),
                parseUuid(payload, "oid"),
                parseUuid(payload, "aid"),
                parseUuid(payload, "act"),
                parseString(payload, "sub"),
                parseString(payload, "email")
        );
    }

    private static UUID parseUuid(Map<?, ?> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String parseString(Map<?, ?> payload, String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }
}
