package com.yowyob.flashshop.service;

import com.yowyob.flashshop.dto.AdminApiTokenDtoSuperAdmin;
import com.yowyob.flashshop.dto.AdminDashboardStatsDtoSuperAdmin;
import com.yowyob.flashshop.dto.AdminVerificationResultDtoSuperAdmin;
import com.yowyob.flashshop.repository.PlatformRepository;
import com.yowyob.flashshop.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for SuperAdmin platform administration.
 * Provides statistics and management for identity verification platforms.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformServiceSuperAdmin {

        private final PlatformRepository platform_repository;
        private final VerificationLogRepository verification_log_repository;
        private final DatabaseClient database_client;
        private final DashboardExportService dashboard_export_service;

        /**
         * Aggregates statistics for the SuperAdmin dashboard.
         *
         * @return a Mono containing the dashboard statistics
         */
        public Mono<AdminDashboardStatsDtoSuperAdmin> getDashboardStats() {
                return Mono.zip(
                                platform_repository.count(),
                                database_client.sql("SELECT COUNT(*) FROM verification_logs")
                                                .map((row, metadata) -> row.get(0, Long.class)).one()
                                                .defaultIfEmpty(0L),
                                database_client.sql("SELECT COUNT(*) FROM verification_logs WHERE status = 'ACCEPTED'")
                                                .map((row, metadata) -> row.get(0, Long.class)).one()
                                                .defaultIfEmpty(0L),
                                database_client.sql("SELECT COUNT(*) FROM verification_logs WHERE status = 'REJECTED'")
                                                .map((row, metadata) -> row.get(0, Long.class)).one()
                                                .defaultIfEmpty(0L),
                                database_client.sql(
                                                "SELECT COALESCE(SUM(processing_time_ms) / NULLIF(COUNT(*), 0), 0.0) FROM verification_logs")
                                                .map((row, metadata) -> row.get(0, Double.class)).one()
                                                .defaultIfEmpty(0.0),
                                platform_repository.findAll().filter(p -> p.getActive() != null && p.getActive())
                                                .count(),
                                platform_repository.count()).flatMap(tuple -> {
                                        AdminDashboardStatsDtoSuperAdmin.AdminDashboardStatsDtoSuperAdminBuilder builder = AdminDashboardStatsDtoSuperAdmin
                                                        .builder()
                                                        .total_users(tuple.getT1())
                                                        .total_verifications(tuple.getT2())
                                                        .successful_verifications(tuple.getT3())
                                                        .failed_verifications(tuple.getT4())
                                                        .pending_verifications(
                                                                        tuple.getT2() - (tuple.getT3() + tuple.getT4()))
                                                        .avg_processing_time_ms(tuple.getT5())
                                                        .active_api_tokens(tuple.getT6())
                                                        .total_api_tokens_created(tuple.getT7());

                                        return getRecentVerifications(10).collectList()
                                                        .flatMap(recents -> {
                                                                builder.last_ten_verifications(recents);

                                                                Map<String, Long> distribution = new HashMap<>();
                                                                distribution.put("SUCCESS", tuple.getT3());
                                                                distribution.put("FAILED", tuple.getT4());
                                                                distribution.put("PENDING", tuple.getT2()
                                                                                - (tuple.getT3() + tuple.getT4()));
                                                                builder.status_distribution(distribution);

                                                                return fetchTrendAndPeakUsage(builder);
                                                        });
                                });
        }

        /**
         * Fetches trends and peak usage data for the dashboard.
         *
         * @param builder the DTO builder being populated
         * @return a Mono containing the updated dashboard stats
         */
        private Mono<AdminDashboardStatsDtoSuperAdmin> fetchTrendAndPeakUsage(
                        AdminDashboardStatsDtoSuperAdmin.AdminDashboardStatsDtoSuperAdminBuilder builder) {
                // Trend: Count per day for last 7 days
                Mono<java.util.List<AdminDashboardStatsDtoSuperAdmin.VerificationsOverTimeDto>> trend = database_client
                                .sql(
                                                "SELECT TO_CHAR(date, 'YYYY-MM-DD') as day, COUNT(*) as count " +
                                                                "FROM verification_logs " +
                                                                "GROUP BY day ORDER BY day DESC LIMIT 7")
                                .map((row, meta) -> AdminDashboardStatsDtoSuperAdmin.VerificationsOverTimeDto.builder()
                                                .label(row.get("day", String.class) != null
                                                                ? row.get("day", String.class)
                                                                : "N/A")
                                                .count(row.get("count", Long.class))
                                                .build())
                                .all().collectList();

                // Peak Usage: Count per hour
                Mono<java.util.List<AdminDashboardStatsDtoSuperAdmin.PeakUsageDto>> peak = database_client.sql(
                                "SELECT EXTRACT(HOUR FROM date) as hr, COUNT(*) as count " +
                                                "FROM verification_logs " +
                                                "GROUP BY hr ORDER BY hr")
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

        /**
         * Retrieves all verification logs.
         *
         * @return a Flux of verification result DTOs
         */
        public Flux<AdminVerificationResultDtoSuperAdmin> getAllVerifications() {
                return verification_log_repository.findAll()
                                .map(this::mapToResultDto);
        }

        /**
         * Retrieves recent verification logs with a limit.
         *
         * @param limit the maximum number of records to return
         * @return a Flux of verification result DTOs
         */
        public Flux<AdminVerificationResultDtoSuperAdmin> getRecentVerifications(int limit) {
                return database_client.sql("SELECT * FROM verification_logs ORDER BY date DESC LIMIT :limit")
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

        /**
         * Generates a verification report for a given ID.
         *
         * @param id the verification log ID
         * @return a Mono containing the report as byte array
         */
        public Mono<byte[]> getVerificationReport(Long id) {
                return dashboard_export_service.exportSingleVerificationPdf(id);
        }

        /**
         * Retrieves all API tokens.
         *
         * @return a Flux of API token DTOs
         */
        public Flux<AdminApiTokenDtoSuperAdmin> getAllTokens() {
                return platform_repository.findAll()
                                .flatMap(p -> database_client
                                                .sql("SELECT COUNT(*), MAX(date) FROM verification_logs WHERE platform_id = :pid")
                                                .bind("pid", p.getId())
                                                .map((row, meta) -> {
                                                        java.util.Date last_call = row.get(1, java.util.Date.class);
                                                        return AdminApiTokenDtoSuperAdmin.builder()
                                                                        .platformName(p.getName())
                                                                        .apiKey(p.getApiKey())
                                                                        .active(p.getActive() != null && p.getActive())
                                                                        .totalCalls(row.get(0, Long.class))
                                                                        .lastCallDate(last_call != null
                                                                                        ? new java.sql.Timestamp(
                                                                                                        last_call.getTime())
                                                                                                        .toLocalDateTime()
                                                                                        : null)
                                                                        .build();
                                                }).one());
        }

        /**
         * Searches for API tokens based on a query.
         *
         * @param query the search query
         * @return a Flux of API token DTOs
         */
        public Flux<AdminApiTokenDtoSuperAdmin> searchTokens(String query) {
                return platform_repository.findAll()
                                .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase())
                                                || p.getEmail().toLowerCase().contains(query.toLowerCase()))
                                .flatMap(p -> database_client
                                                .sql("SELECT COUNT(*), MAX(date) FROM verification_logs WHERE platform_id = :pid")
                                                .bind("pid", p.getId())
                                                .map((row, meta) -> {
                                                        java.util.Date last_call = row.get(1, java.util.Date.class);
                                                        return AdminApiTokenDtoSuperAdmin.builder()
                                                                        .platformName(p.getName())
                                                                        .apiKey(p.getApiKey())
                                                                        .active(p.getActive() != null && p.getActive())
                                                                        .totalCalls(row.get(0, Long.class))
                                                                        .lastCallDate(last_call != null
                                                                                        ? new java.sql.Timestamp(
                                                                                                        last_call.getTime())
                                                                                                        .toLocalDateTime()
                                                                                        : null)
                                                                        .build();
                                                }).one());
        }

        /**
         * Maps a verification log entity to a DTO.
         *
         * @param log the verification log entity
         * @return the verification result DTO
         */
        private AdminVerificationResultDtoSuperAdmin mapToResultDto(com.yowyob.flashshop.model.VerificationLog log) {
                return AdminVerificationResultDtoSuperAdmin.builder()
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
                                .build();
        }
}
