package com.yowyob.flashshop.service;

import com.yowyob.flashshop.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Comprehensive metrics service for the VerifID dashboard.
 * All queries support optional filtering by platformId and date range.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

        private final DatabaseClient db;

        // ─────────────────────────────────────────────────────────────────────────
        // Helper: build a WHERE clause fragment from optional filters
        // ─────────────────────────────────────────────────────────────────────────

        private String dateFilter(LocalDateTime from, LocalDateTime to) {
                if (from != null && to != null) {
                        return " AND vl.date BETWEEN '" + from + "' AND '" + to + "'";
                } else if (from != null) {
                        return " AND vl.date >= '" + from + "'";
                } else if (to != null) {
                        return " AND vl.date <= '" + to + "'";
                }
                return "";
        }

        private String platformFilter(Long platform_id) {
                return platform_id != null ? " AND vl.platform_id = " + platform_id : "";
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 1. KPI STATS
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns aggregated KPIs for the dashboard header cards.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @param period      label string to embed in the response ("7d", "30d", etc.)
         * @return a Mono containing the dashboard statistics
         */
        public Mono<DashboardStatsDto> getDashboardStats(
                        Long platform_id, LocalDateTime from, LocalDateTime to, String period) {

                String where_base = "WHERE 1=1" + platformFilter(platform_id) + dateFilter(from, to);
                String tokens_filter = platform_id != null ? " AND p.id = " + platform_id : "";
                String users_filter = platform_id != null ? " WHERE id = " + platform_id : "";

                String sql = "SELECT " +
                                "  COUNT(*)                                                              AS total, " +
                                "  SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END)             AS success, " +
                                "  SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END)             AS failure, " +
                                "  SUM(CASE WHEN vl.status = 'PENDING' THEN 1 ELSE 0 END)              AS pending, " +
                                "  CAST(AVG(vl.processing_time_ms) AS DOUBLE PRECISION)                AS avg_ms, " +
                                "  (SELECT COUNT(DISTINCT p.api_key) FROM platforms p WHERE p.active = TRUE"
                                + tokens_filter + ") AS tokens, " +
                                "  (SELECT COUNT(*) FROM platforms" + users_filter
                                + ")                                    AS users " +
                                "FROM verification_logs vl " +
                                where_base;

                return db.sql(sql)
                                .map((row, md) -> DashboardStatsDto.builder()
                                                .total_verifications(row.get("total", Long.class))
                                                .success_count(row.get("success", Long.class))
                                                .failure_count(row.get("failure", Long.class))
                                                .pending_count(row.get("pending", Long.class))
                                                .total_users(row.get("users", Long.class))
                                                .avg_processing_time_ms(row.get("avg_ms", Double.class))
                                                .total_api_tokens_created(row.get("tokens", Long.class))
                                                .period(period)
                                                .build())
                                .one()
                                .defaultIfEmpty(DashboardStatsDto.builder()
                                                .total_verifications(0L).success_count(0L).failure_count(0L)
                                                .pending_count(0L).total_users(0L)
                                                .avg_processing_time_ms(0.0).total_api_tokens_created(0L).period(period)
                                                .build());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 2. VERIFICATIONS OVER TIME (line chart)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns time-series data for the line chart.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @param granularity "day" | "week" | "month"
         * @return a Flux of time-series points
         */
        public Flux<TimeSeriesPoint> getVerificationsOverTime(
                        Long platform_id, LocalDateTime from, LocalDateTime to, String granularity) {

                String trunc = switch (granularity != null ? granularity.toLowerCase() : "day") {
                        case "week" -> "week";
                        case "month" -> "month";
                        default -> "day";
                };

                String date_format = switch (trunc) {
                        case "week" -> "YYYY-IW"; // ISO week
                        case "month" -> "YYYY-MM";
                        default -> "YYYY-MM-DD";
                };

                String where_base = "WHERE 1=1" + platformFilter(platform_id) + dateFilter(from, to);
                String sql = "SELECT " +
                                "  TO_CHAR(DATE_TRUNC('" + trunc + "', vl.date), '" + date_format + "')     AS label, "
                                +
                                "  COUNT(*)                                                                 AS total, "
                                +
                                "  SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END)                AS success, "
                                +
                                "  SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END)                AS failure " +
                                "FROM verification_logs vl " +
                                where_base +
                                " GROUP BY DATE_TRUNC('" + trunc + "', vl.date)" +
                                " ORDER BY DATE_TRUNC('" + trunc + "', vl.date)";

                return db.sql(sql)
                                .map((row, md) -> TimeSeriesPoint.builder()
                                                .label(row.get("label", String.class))
                                                .count(row.get("total", Long.class))
                                                .success_count(row.get("success", Long.class))
                                                .failure_count(row.get("failure", Long.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 3. HOURLY TRAFFIC (bar chart)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns hourly traffic distribution.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @return a Flux of hourly traffic data
         */
        public Flux<HourlyTrafficDto> getHourlyTraffic(
                        Long platform_id, LocalDateTime from, LocalDateTime to) {

                String where_base = "WHERE 1=1" + platformFilter(platform_id) + dateFilter(from, to);
                String sql = "SELECT EXTRACT(HOUR FROM vl.date)::INTEGER AS hour, COUNT(*) AS cnt " +
                                "FROM verification_logs vl " +
                                where_base +
                                " GROUP BY EXTRACT(HOUR FROM vl.date)" +
                                " ORDER BY hour";

                return db.sql(sql)
                                .map((row, md) -> HourlyTrafficDto.builder()
                                                .hour(row.get("hour", Integer.class))
                                                .count(row.get("cnt", Long.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 4. STATUS DISTRIBUTION (pie/donut chart)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns verification status distribution.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @return a Flux of status distribution data
         */
        public Flux<StatusDistributionDto> getStatusDistribution(
                        Long platform_id, LocalDateTime from, LocalDateTime to) {

                String where_base = "WHERE 1=1" + platformFilter(platform_id) + dateFilter(from, to);
                String sql = "SELECT vl.status, COUNT(*) AS cnt, " +
                                "  CAST(COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER(), 0) AS DOUBLE PRECISION) AS pct "
                                +
                                "FROM verification_logs vl " +
                                where_base +
                                " GROUP BY vl.status" +
                                " ORDER BY cnt DESC";

                return db.sql(sql)
                                .map((row, md) -> StatusDistributionDto.builder()
                                                .status(row.get("status", String.class))
                                                .count(row.get("cnt", Long.class))
                                                .percentage(row.get("pct", Double.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 5. DOC TYPE BREAKDOWN (horizontal bar chart)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns breakdown of verifications by document type.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @return a Flux of document type breakdown data
         */
        public Flux<DocTypeBreakdownDto> getDocTypeBreakdown(
                        Long platform_id, LocalDateTime from, LocalDateTime to) {

                String where_base = "WHERE 1=1" + platformFilter(platform_id) + dateFilter(from, to);
                String sql = "SELECT " +
                                "  vl.doc_type, " +
                                "  COUNT(*) AS total, " +
                                "  SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS success, " +
                                "  SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END) AS failure, " +
                                "  CAST(SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) * 100.0 " +
                                "       / NULLIF(COUNT(*), 0) AS DOUBLE PRECISION) AS success_rate, " +
                                "  CAST(AVG(vl.processing_time_ms) AS DOUBLE PRECISION) AS avg_ms " +
                                "FROM verification_logs vl " +
                                where_base +
                                " GROUP BY vl.doc_type" +
                                " ORDER BY total DESC";

                return db.sql(sql)
                                .map((row, md) -> DocTypeBreakdownDto.builder()
                                                .doc_type(row.get("doc_type", String.class))
                                                .total(row.get("total", Long.class))
                                                .success_count(row.get("success", Long.class))
                                                .failure_count(row.get("failure", Long.class))
                                                .success_rate(row.get("success_rate", Double.class))
                                                .avg_processing_time_ms(row.get("avg_ms", Double.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 6. RECENT VERIFICATIONS (last 10 with platform name)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns the 10 most recent verifications.
         *
         * @param platform_id optional – filter to a single platform
         * @return a Flux of recent verification data
         */
        public Flux<RecentVerificationDto> getRecentVerifications(Long platform_id) {
                String where_clause = platform_id != null
                                ? "WHERE vl.platform_id = " + platform_id
                                : "WHERE 1=1";

                String sql = "SELECT vl.id, vl.platform_id, p.name AS platform_name, vl.date, " +
                                "       vl.doc_type, vl.status, vl.reason, vl.confidence, vl.processing_time_ms " +
                                "FROM verification_logs vl " +
                                "JOIN platforms p ON p.id = vl.platform_id " +
                                where_clause +
                                " ORDER BY vl.date DESC LIMIT 10";

                return db.sql(sql)
                                .map((row, md) -> RecentVerificationDto.builder()
                                                .id(row.get("id", Long.class))
                                                .platform_id(row.get("platform_id", Long.class))
                                                .platform_name(row.get("platform_name", String.class))
                                                .date(row.get("date", java.time.LocalDateTime.class))
                                                .doc_type(row.get("doc_type", String.class))
                                                .status(row.get("status", String.class))
                                                .reason(row.get("reason", String.class))
                                                .confidence(row.get("confidence", Double.class))
                                                .processing_time_ms(row.get("processing_time_ms", Integer.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 6.5 DETAILED SUCCESSFUL VERIFICATIONS
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns detailed data for successful verifications.
         *
         * @param platform_id optional – filter to a single platform
         * @param from        optional – start of period
         * @param to          optional – end of period
         * @return a Flux of detailed verification data
         */
        public Flux<DetailedVerificationDto> getSuccessfulVerifications(Long platform_id, LocalDateTime from,
                        LocalDateTime to) {
                String where_base = "WHERE vl.status = 'ACCEPTED'" + platformFilter(platform_id) + dateFilter(from, to);
                String sql = "SELECT vl.id, vl.platform_id, p.name AS platform_name, vl.date, " +
                                "       vl.doc_type, vl.status, vl.confidence, vl.processing_time_ms, " +
                                "       vl.document_number, vl.holder_name, vl.date_of_birth, vl.issue_date, vl.expiry_date, vl.additional_fields "
                                +
                                "FROM verification_logs vl " +
                                "JOIN platforms p ON p.id = vl.platform_id " +
                                where_base +
                                " ORDER BY vl.date DESC";

                return db.sql(sql)
                                .map((row, md) -> DetailedVerificationDto.builder()
                                                .id(row.get("id", Long.class))
                                                .platform_id(row.get("platform_id", Long.class))
                                                .platform_name(row.get("platform_name", String.class))
                                                .date(row.get("date", java.time.LocalDateTime.class))
                                                .doc_type(row.get("doc_type", String.class))
                                                .status(row.get("status", String.class))
                                                .confidence(row.get("confidence", Double.class))
                                                .processing_time_ms(row.get("processing_time_ms", Integer.class))
                                                .document_number(row.get("document_number", String.class))
                                                .holder_name(row.get("holder_name", String.class))
                                                .date_of_birth(row.get("date_of_birth", String.class))
                                                .issue_date(row.get("issue_date", String.class))
                                                .expiry_date(row.get("expiry_date", String.class))
                                                .additional_fields(row.get("additional_fields", String.class))
                                                .build())
                                .all();
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 7. LEGACY — kept for backward compat
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns overview of usage statistics across all platforms.
         *
         * @return a Flux of usage statistics
         */
        public Flux<UsageStatisticDto> getOverallUsageStatistics() {
                String sql = "SELECT platform_id, doc_type, COUNT(*) AS total_logs, " +
                                "CAST(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS success_rate, "
                                +
                                "CAST(SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS failure_rate, "
                                +
                                "CAST(AVG(confidence) AS DOUBLE PRECISION) AS avg_confidence, " +
                                "STRING_AGG(DISTINCT reason, ', ') AS rejection_reasons " +
                                "FROM verification_logs GROUP BY platform_id, doc_type";

                return db.sql(sql)
                                .map((row, md) -> UsageStatisticDto.builder()
                                                .platform_id(row.get("platform_id", Long.class))
                                                .doc_type(row.get("doc_type", String.class))
                                                .total_logs(row.get("total_logs", Long.class))
                                                .success_rate(row.get("success_rate", Double.class))
                                                .failure_rate(row.get("failure_rate", Double.class))
                                                .avg_confidence(row.get("avg_confidence", Double.class))
                                                .rejection_reasons(row.get("rejection_reasons", String.class))
                                                .build())
                                .all();
        }

        /**
         * Returns usage statistics filtered by platform.
         *
         * @param platform_id the identifier of the platform
         * @return a Flux of usage statistics
         */
        public Flux<UsageStatisticDto> getUsageStatisticsByPlatform(Long platform_id) {
                String sql = "SELECT platform_id, doc_type, COUNT(*) AS total_logs, " +
                                "CAST(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS success_rate, "
                                +
                                "CAST(SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS failure_rate, "
                                +
                                "CAST(AVG(confidence) AS DOUBLE PRECISION) AS avg_confidence, " +
                                "STRING_AGG(DISTINCT reason, ', ') AS rejection_reasons " +
                                "FROM verification_logs WHERE platform_id = :platformId GROUP BY platform_id, doc_type";

                return db.sql(sql)
                                .bind("platformId", platform_id)
                                .map((row, md) -> UsageStatisticDto.builder()
                                                .platform_id(row.get("platform_id", Long.class))
                                                .doc_type(row.get("doc_type", String.class))
                                                .total_logs(row.get("total_logs", Long.class))
                                                .success_rate(row.get("success_rate", Double.class))
                                                .failure_rate(row.get("failure_rate", Double.class))
                                                .avg_confidence(row.get("avg_confidence", Double.class))
                                                .rejection_reasons(row.get("rejection_reasons", String.class))
                                                .build())
                                .all();
        }
}
