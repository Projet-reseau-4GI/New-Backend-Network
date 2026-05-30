package com.projects.controller;

import com.projects.dto.AdminApiTokenDtoSuperAdmin;
import com.projects.dto.AdminDashboardStatsDtoSuperAdmin;
import com.projects.dto.AdminVerificationResultDtoSuperAdmin;
import com.projects.service.AdminPlatformServiceSuperAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST Controller for SuperAdmin platform administration.
 * Provides endpoints for dashboard statistics, verification logs, and API token
 * management.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminPlatformControllerSuperAdmin {

    private final AdminPlatformServiceSuperAdmin admin_platform_service_super_admin;

    /**
     * Retrieves dashboard statistics for SuperAdmin.
     *
     * @return a Mono containing the dashboard statistics
     */
    @GetMapping("/dashboard/statsSuperAdmin")
    public Mono<AdminDashboardStatsDtoSuperAdmin> getStats() {
        return admin_platform_service_super_admin.getDashboardStats();
    }

    /**
     * Retrieves all verification logs.
     *
     * @return a Flux of verification result DTOs
     */
    @GetMapping("/verificationsSuperAdmin")
    public Flux<AdminVerificationResultDtoSuperAdmin> getVerifications() {
        return admin_platform_service_super_admin.getAllVerifications();
    }

    /**
     * Downloads a verification report as PDF.
     *
     * @param id the verification log ID
     * @return a Mono containing the PDF response entity
     */
    @GetMapping("/verifications/{id}/reportSuperAdmin")
    public Mono<ResponseEntity<byte[]>> getReport(@PathVariable Long id) {
        return admin_platform_service_super_admin.getVerificationReport(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=verification-report-" + id + ".pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes));
    }

    /**
     * Retrieves all API tokens.
     *
     * @return a Flux of API token DTOs
     */
    @GetMapping("/tokensSuperAdmin")
    public Flux<AdminApiTokenDtoSuperAdmin> getTokens() {
        return admin_platform_service_super_admin.getAllTokens();
    }

    /**
     * Searches for API tokens.
     *
     * @param query the search query
     * @return a Flux of matching API token DTOs
     */
    @GetMapping("/tokens/searchSuperAdmin")
    public Flux<AdminApiTokenDtoSuperAdmin> searchTokens(@RequestParam String query) {
        return admin_platform_service_super_admin.searchTokens(query);
    }
}
