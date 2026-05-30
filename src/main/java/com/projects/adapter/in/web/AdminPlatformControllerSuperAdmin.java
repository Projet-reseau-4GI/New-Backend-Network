package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.AdminApiTokenDtoSuperAdmin;
import com.projects.adapter.in.web.dto.AdminDashboardStatsDtoSuperAdmin;
import com.projects.adapter.in.web.dto.AdminVerificationResultDtoSuperAdmin;
import com.projects.application.port.in.admin.AdminDashboardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound adapter (Web) — SuperAdmin platform administration.
 * Injects AdminDashboardUseCase port — no direct service coupling.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "SuperAdmin — Dashboard", description = "Administration globale : statistiques, tokens, vérifications")
public class AdminPlatformControllerSuperAdmin {

    private final AdminDashboardUseCase adminDashboardUseCase;

    @GetMapping("/dashboard/statsSuperAdmin")
    @Operation(summary = "Statistiques globales SuperAdmin")
    public Mono<AdminDashboardStatsDtoSuperAdmin> getStats() {
        return adminDashboardUseCase.getDashboardStats();
    }

    @GetMapping("/verificationsSuperAdmin")
    @Operation(summary = "Toutes les vérifications")
    public Flux<AdminVerificationResultDtoSuperAdmin> getVerifications() {
        return adminDashboardUseCase.getAllVerifications();
    }

    @GetMapping("/verifications/{id}/reportSuperAdmin")
    @Operation(summary = "Télécharger rapport PDF d'une vérification")
    public Mono<ResponseEntity<byte[]>> getReport(@PathVariable Long id) {
        return adminDashboardUseCase.getVerificationReport(id)
            .map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=verification-report-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes));
    }

    @GetMapping("/tokensSuperAdmin")
    @Operation(summary = "Tous les tokens API")
    public Flux<AdminApiTokenDtoSuperAdmin> getTokens() {
        return adminDashboardUseCase.getAllTokens();
    }

    @GetMapping("/tokens/searchSuperAdmin")
    @Operation(summary = "Rechercher des tokens API")
    public Flux<AdminApiTokenDtoSuperAdmin> searchTokens(@RequestParam String query) {
        return adminDashboardUseCase.searchTokens(query);
    }
}
