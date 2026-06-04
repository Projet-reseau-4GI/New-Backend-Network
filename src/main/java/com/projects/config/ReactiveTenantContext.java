package com.projects.config;

import com.projects.adapter.out.kernel.KernelTokenClaims;
import com.projects.model.Platform;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Context helper for the reactive Spring WebFlux environment.
 *
 * Stocke et récupère :
 *  - La Platform locale (tenant VerifID, maintenu pour la rétrocompatibilité)
 *  - Le KernelTenantContext (tenantId, organizationId, userId, actorId du kernel)
 */
public class ReactiveTenantContext {

    public static final String TENANT_KEY        = "CURRENT_PLATFORM_TENANT";
    public static final String KERNEL_TENANT_KEY = "KERNEL_TENANT_CONTEXT";

    // ----------------------------------------------------------------
    // Platform locale (rétrocompatibilité)
    // ----------------------------------------------------------------

    public static Context putPlatform(Context context, Platform platform) {
        return context.put(TENANT_KEY, platform);
    }

    public static Mono<Platform> getPlatform() {
        return Mono.deferContextual(ctx ->
                ctx.hasKey(TENANT_KEY) ? Mono.just(ctx.get(TENANT_KEY)) : Mono.empty());
    }

    // ----------------------------------------------------------------
    // Kernel Tenant Context (alimenté par KernelTenantContextFilter)
    // ----------------------------------------------------------------

    /**
     * Stocke les claims du kernel (tenantId, orgId, userId, actorId)
     * dans le contexte réactif courant.
     */
    public static Context putKernelClaims(Context context, KernelTokenClaims claims) {
        return context.put(KERNEL_TENANT_KEY, claims);
    }

    /**
     * Récupère les claims kernel du contexte réactif.
     * Retourne Mono.empty() si aucun token kernel n'est présent.
     */
    public static Mono<KernelTokenClaims> getKernelClaims() {
        return Mono.deferContextual(ctx ->
                ctx.hasKey(KERNEL_TENANT_KEY)
                        ? Mono.just(ctx.get(KERNEL_TENANT_KEY))
                        : Mono.empty());
    }

    /**
     * Retourne le tenantId kernel si disponible, null sinon.
     */
    public static Mono<UUID> getKernelTenantId() {
        return getKernelClaims().mapNotNull(KernelTokenClaims::tenantId);
    }

    /**
     * Retourne l'organizationId kernel si disponible.
     */
    public static Mono<UUID> getKernelOrganizationId() {
        return getKernelClaims().mapNotNull(KernelTokenClaims::organizationId);
    }

    /**
     * Retourne l'userId kernel si disponible.
     */
    public static Mono<UUID> getKernelUserId() {
        return getKernelClaims().mapNotNull(KernelTokenClaims::userId);
    }
}
