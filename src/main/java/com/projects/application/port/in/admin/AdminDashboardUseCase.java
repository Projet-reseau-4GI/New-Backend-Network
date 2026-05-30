package com.projects.application.port.in.admin;

import com.projects.adapter.in.web.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inbound port — SuperAdmin dashboard and platform management use cases.
 */
public interface AdminDashboardUseCase {
    Mono<AdminDashboardStatsDtoSuperAdmin> getDashboardStats();
    Flux<AdminVerificationResultDtoSuperAdmin> getAllVerifications();
    Flux<AdminVerificationResultDtoSuperAdmin> getRecentVerifications(int limit);
    Flux<AdminApiTokenDtoSuperAdmin> getAllTokens();
    Flux<AdminApiTokenDtoSuperAdmin> searchTokens(String query);
    Mono<byte[]> getVerificationReport(Long id);
}
