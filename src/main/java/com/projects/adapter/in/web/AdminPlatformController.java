package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.PlatformResponse;
import com.projects.model.Platform;
import com.projects.service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Super-admin controller for platform management.
 *
 * Responsabilités :
 * - Lister toutes les plateformes enregistrées.
 * - Activer / désactiver une plateforme.
 *
 * Ce qui a été supprimé (et pourquoi) :
 * - POST /api/admin/platforms (créer une plateforme sans mot de passe ni OTP)
 * → Remplacé par le flux complet POST /api/auth/register + /verify-email.
 * - POST /api/admin/platforms/{id}/generate-key (régénérer la clé sans OTP)
 * → Remplacé par POST /api/auth/regenerate-token + /confirm-regenerate (avec
 * OTP).
 *
 * Les deux endpoints supprimés contournaient la sécurité (pas de mot de passe,
 * pas de vérification email) et créaient des entités incomplètes en base.
 */
@RestController
@RequestMapping("/api/admin/platforms")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Administration", description = "Gestion des plateformes — réservé aux super-admins")
public class AdminPlatformController {

    private final PlatformService platformService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. LISTER TOUTES LES PLATEFORMES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Lister toutes les plateformes", description = "Retourne l'ensemble des plateformes enregistrées dans le système.", security = @SecurityRequirement(name = "bearerAuth"))
    public Flux<Platform> getAllPlatforms() {
        log.info("Admin: listing all platforms");
        return platformService.getAllPlatforms();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. ACTIVER / DÉSACTIVER UNE PLATEFORME
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/toggle-status")
    @Operation(summary = "Activer ou désactiver une plateforme", description = "Inverse l'état actif/inactif d'une plateforme. "
            +
            "Une plateforme inactive ne peut plus utiliser l'API de vérification.", security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Platform> toggleStatus(@PathVariable Long id) {
        log.info("Admin: toggling status for platform id={}", id);
        return platformService.toggleStatus(id);
    }
    // ─────────────────────────────────────────────────────────────────────────
    // 3. STATISTIQUES DES TOKENS API (SUPER ADMIN)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/tokens")
    @Operation(summary = "Obtenir les statistiques des tokens API", description = "Retourne la liste des plateformes, le nombre d'appels et la date du dernier appel. Supporte la recherche par nom, email ou clé.", security = @SecurityRequirement(name = "bearerAuth"))
    public Flux<com.projects.adapter.in.web.dto.PlatformTokenStatsDto> getPlatformTokenStats(
            @RequestParam(required = false) String search) {
        log.info("Admin: listing platform token stats with search={}", search);
        return platformService.getPlatformTokenStats(search);
    }
}
