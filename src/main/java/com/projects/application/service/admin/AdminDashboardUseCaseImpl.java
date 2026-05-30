package com.projects.application.service.admin;

import com.projects.application.port.in.admin.AdminDashboardUseCase;
import com.projects.application.port.out.VerificationLogRepositoryPort;
import com.projects.application.port.out.PlatformRepositoryPort;
import com.projects.adapter.in.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Application use case — SuperAdmin dashboard and platform management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardUseCaseImpl implements AdminDashboardUseCase {

    private final PlatformRepositoryPort platformRepository;
    private final VerificationLogRepositoryPort verificationLogRepository;
    private final DatabaseClient databaseClient;
    private final com.projects.application.service.metrics.DashboardExportApplicationService exportService;

    @Override
    public Mono<AdminDashboardStatsDtoSuperAdmin> getDashboardStats() {
        return Mono.zip(
            platformRepository.count(),
            databaseClient.sql("SELECT COUNT(*) FROM verification_logs")
                .map((row, metadata) -> row.get(0, Long.class)).one().defaultIfEmpty(0L),
            databaseClient.sql("SELECT COUNT(*) FROM verification_logs WHERE status = 'ACCEPTED'")
                .map((row, metadata) -> row.get(0, Long.class)).one().defaultIfEmpty(0L),
            databaseClient.sql("SELECT COUNT(*) FROM verification_logs WHERE status = 'REJECTED'")
                .map((row, metadata) -> row.get(0, Long.class)).one().defaultIfEmpty(0L),
            databaseClient.sql("SELECT COALESCE(SUM(processing_time_ms) / NULLIF(COUNT(*), 0), 0.0) FROM verification_logs")
                .map((row, metadata) -> row.get(0, Double.class)).one().defaultIfEmpty(0.0),
            platformRepository.findAll().filter(p -> p.getActive() != null && p.getActive()).count(),
            platformRepository.count()
        ).flatMap(tuple -> {
            var builder = AdminDashboardStatsDtoSuperAdmin.builder()
                .total_users(tuple.getT1())
                .total_verifications(tuple.getT2())
                .successful_verifications(tuple.getT3())
                .failed_verifications(tuple.getT4())
                .pending_verifications(tuple.getT2() - (tuple.getT3() + tuple.getT4()))
                .avg_processing_time_ms(tuple.getT5())
                .active_api_tokens(tuple.getT6())
                .total_api_tokens_created(tuple.getT7());

            return getRecentVerifications(10).collectList()
                .flatMap(recents -> {
                    builder.last_ten_verifications(recents);
                    Map<String, Long> distribution = new HashMap<>();
                    distribution.put("SUCCESS", tuple.getT3());
                    distribution.put("FAILED", tuple.getT4());
                    distribution.put("PENDING", tuple.getT2() - (tuple.getT3() + tuple.getT4()));
                    builder.status_distribution(distribution);
                    return fetchTrendAndPeakUsage(builder);
                });
        });
    }

    private Mono<AdminDashboardStatsDtoSuperAdmin> fetchTrendAndPeakUsage(
        AdminDashboardStatsDtoSuperAdmin.AdminDashboardStatsDtoSuperAdminBuilder builder) {
        Mono<java.util.List<AdminDashboardStatsDtoSuperAdmin.VerificationsOverTimeDto>> trend = databaseClient
            .sql("SELECT TO_CHAR(date, 'YYYY-MM-DD') as day, COUNT(*) as count " +
                 "FROM verification_logs GROUP BY day ORDER BY day DESC LIMIT 7")
            .map((row, meta) -> AdminDashboardStatsDtoSuperAdmin.VerificationsOverTimeDto.builder()
                .label(row.get("day", String.class) != null ? row.get("day", String.class) : "N/A")
                .count(row.get("count", Long.class))
                .build())
            .all().collectList();

        Mono<java.util.List<AdminDashboardStatsDtoSuperAdmin.PeakUsageDto>> peak = databaseClient.sql(
            "SELECT EXTRACT(HOUR FROM date) as hr, COUNT(*) as count " +
            "FROM verification_logs GROUP BY hr ORDER BY hr")
            .map((row, meta) -> AdminDashboardStatsDtoSuperAdmin.PeakUsageDto.builder()
                .hour(row.get("hr") != null ? ((Number) row.get("hr")).intValue() : 0)
                .count(row.get("count", Long.class))
                .build())
            .all().collectList();

        return Mono.zip(trend, peak).map(tuple -> {
            builder.verifications_trend(tuple.getT1());
            builder.peak_usage(tuple.getT2());
            return builder.build();
        });
    }

    @Override
    public Flux<AdminVerificationResultDtoSuperAdmin> getAllVerifications() {
        return verificationLogRepository.findAll()
            .map(log -> AdminVerificationResultDtoSuperAdmin.builder()
                .id(String.valueOf(log.getId()))
                .date(log.getDate())
                .doc_type(log.getDocType())
                .status(log.getStatus())
                .processing_time_ms(log.getProcessingTimeMs())
                .document_number(log.getDocumentNumber())
                .holder_name(log.getHolderName())
                .date_of_birth(log.getDateOfBirth())
                .issue_date(log.getIssueDate())
                .expiry_date(log.getExpiryDate())
                .confidence_score(log.getConfidence())
                .additional_fields(log.getAdditionalFields())
                .build());
    }

    @Override
    public Flux<AdminVerificationResultDtoSuperAdmin> getRecentVerifications(int limit) {
        return databaseClient.sql("SELECT * FROM verification_logs ORDER BY date DESC LIMIT :limit")
            .bind("limit", limit)
            .map((row, meta) -> AdminVerificationResultDtoSuperAdmin.builder()
                .id(String.valueOf(row.get("id", Long.class)))
                .date(row.get("date", java.time.LocalDateTime.class))
                .doc_type(row.get("doc_type", String.class))
                .status(row.get("status", String.class))
                .processing_time_ms(row.get("processing_time_ms", Integer.class))
                .document_number(row.get("document_number", String.class))
                .holder_name(row.get("holder_name", String.class))
                .confidence_score(row.get("confidence", Double.class))
                .build())
            .all();
    }

    @Override
    public Flux<AdminApiTokenDtoSuperAdmin> getAllTokens() {
        return platformRepository.findAll()
            .flatMap(p -> databaseClient
                .sql("SELECT COUNT(*), MAX(date) FROM verification_logs WHERE platform_id = :pid")
                .bind("pid", p.getId())
                .map((row, meta) -> {
                    java.util.Date lastCall = row.get(1, java.util.Date.class);
                    return AdminApiTokenDtoSuperAdmin.builder()
                        .platformName(p.getName())
                        .apiKey(p.getApiKey())
                        .active(p.getActive() != null && p.getActive())
                        .totalCalls(row.get(0, Long.class))
                        .lastCallDate(lastCall != null
                            ? new java.sql.Timestamp(lastCall.getTime()).toLocalDateTime()
                            : null)
                        .build();
                }).one());
    }

    @Override
    public Flux<AdminApiTokenDtoSuperAdmin> searchTokens(String query) {
        return platformRepository.findAll()
            .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase()) ||
                         p.getEmail().toLowerCase().contains(query.toLowerCase()))
            .flatMap(p -> databaseClient
                .sql("SELECT COUNT(*), MAX(date) FROM verification_logs WHERE platform_id = :pid")
                .bind("pid", p.getId())
                .map((row, meta) -> {
                    java.util.Date lastCall = row.get(1, java.util.Date.class);
                    return AdminApiTokenDtoSuperAdmin.builder()
                        .platformName(p.getName())
                        .apiKey(p.getApiKey())
                        .active(p.getActive() != null && p.getActive())
                        .totalCalls(row.get(0, Long.class))
                        .lastCallDate(lastCall != null
                            ? new java.sql.Timestamp(lastCall.getTime()).toLocalDateTime()
                            : null)
                        .build();
                }).one());
    }

    @Override
    public Mono<byte[]> getVerificationReport(Long id) {
        return exportService.exportSingleVerificationPdf(id);
    }
}
