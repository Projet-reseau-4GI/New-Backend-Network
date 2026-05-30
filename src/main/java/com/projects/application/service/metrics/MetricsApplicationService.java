package com.projects.application.service.metrics;

import com.projects.adapter.in.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Application service — Dashboard metrics queries (used by both use case and export).
 */
@Service
@RequiredArgsConstructor
public class MetricsApplicationService {

    private final DatabaseClient db;

    private String dateFilter(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) return " AND vl.date BETWEEN '" + from + "' AND '" + to + "'";
        else if (from != null) return " AND vl.date >= '" + from + "'";
        else if (to != null) return " AND vl.date <= '" + to + "'";
        return "";
    }

    private String platformFilter(Long platformId) {
        return platformId != null ? " AND vl.platform_id = " + platformId : "";
    }

    public Mono<DashboardStatsDto> getDashboardStats(Long platformId, LocalDateTime from, LocalDateTime to, String period) {
        String whereBase = "WHERE 1=1" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS success, " +
            "  SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END) AS failure, " +
            "  SUM(CASE WHEN vl.status = 'PENDING' THEN 1 ELSE 0 END) AS pending, " +
            "  CAST(AVG(vl.processing_time_ms) AS DOUBLE PRECISION) AS avg_ms, " +
            "  (SELECT COUNT(DISTINCT p.api_key) FROM platforms p WHERE p.active = TRUE) AS tokens, " +
            "  (SELECT COUNT(*) FROM platforms) AS users " +
            "FROM verification_logs vl " + whereBase;

        return db.sql(sql)
            .map((row, md) -> DashboardStatsDto.builder()
                .totalVerifications(row.get("total", Long.class))
                .successCount(row.get("success", Long.class))
                .failureCount(row.get("failure", Long.class))
                .pendingCount(row.get("pending", Long.class))
                .totalUsers(row.get("users", Long.class))
                .avgProcessingTimeMs(row.get("avg_ms", Double.class))
                .totalApiTokensCreated(row.get("tokens", Long.class))
                .period(period)
                .build())
            .one()
            .defaultIfEmpty(DashboardStatsDto.builder()
                .totalVerifications(0L).successCount(0L).failureCount(0L).pendingCount(0L)
                .totalUsers(0L).avgProcessingTimeMs(0.0).totalApiTokensCreated(0L).period(period).build());
    }

    public Flux<TimeSeriesPoint> getVerificationsOverTime(Long platformId, LocalDateTime from, LocalDateTime to, String granularity) {
        String trunc = switch (granularity != null ? granularity.toLowerCase() : "day") {
            case "week" -> "week";
            case "month" -> "month";
            default -> "day";
        };
        String dateFormat = switch (trunc) {
            case "week" -> "YYYY-IW";
            case "month" -> "YYYY-MM";
            default -> "YYYY-MM-DD";
        };
        String whereBase = "WHERE 1=1" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT TO_CHAR(DATE_TRUNC('" + trunc + "', vl.date), '" + dateFormat + "') AS label, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END) AS failure " +
            "FROM verification_logs vl " + whereBase +
            " GROUP BY DATE_TRUNC('" + trunc + "', vl.date) ORDER BY DATE_TRUNC('" + trunc + "', vl.date)";

        return db.sql(sql)
            .map((row, md) -> TimeSeriesPoint.builder()
                .label(row.get("label", String.class))
                .count(row.get("total", Long.class))
                .successCount(row.get("success", Long.class))
                .failureCount(row.get("failure", Long.class))
                .build()).all();
    }

    public Flux<HourlyTrafficDto> getHourlyTraffic(Long platformId, LocalDateTime from, LocalDateTime to) {
        String whereBase = "WHERE 1=1" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT EXTRACT(HOUR FROM vl.date)::INTEGER AS hour, COUNT(*) AS cnt FROM verification_logs vl " +
            whereBase + " GROUP BY EXTRACT(HOUR FROM vl.date) ORDER BY hour";
        return db.sql(sql)
            .map((row, md) -> HourlyTrafficDto.builder().hour(row.get("hour", Integer.class)).count(row.get("cnt", Long.class)).build()).all();
    }

    public Flux<StatusDistributionDto> getStatusDistribution(Long platformId, LocalDateTime from, LocalDateTime to) {
        String whereBase = "WHERE 1=1" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT vl.status, COUNT(*) AS cnt, " +
            "CAST(COUNT(*) * 100.0 / NULLIF(SUM(COUNT(*)) OVER(), 0) AS DOUBLE PRECISION) AS pct " +
            "FROM verification_logs vl " + whereBase + " GROUP BY vl.status ORDER BY cnt DESC";
        return db.sql(sql)
            .map((row, md) -> StatusDistributionDto.builder()
                .status(row.get("status", String.class))
                .count(row.get("cnt", Long.class))
                .percentage(row.get("pct", Double.class))
                .build()).all();
    }

    public Flux<DocTypeBreakdownDto> getDocTypeBreakdown(Long platformId, LocalDateTime from, LocalDateTime to) {
        String whereBase = "WHERE 1=1" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT vl.doc_type, COUNT(*) AS total, " +
            "SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) AS success, " +
            "SUM(CASE WHEN vl.status = 'REJECTED' THEN 1 ELSE 0 END) AS failure, " +
            "CAST(SUM(CASE WHEN vl.status = 'ACCEPTED' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS DOUBLE PRECISION) AS success_rate, " +
            "CAST(AVG(vl.processing_time_ms) AS DOUBLE PRECISION) AS avg_ms " +
            "FROM verification_logs vl " + whereBase + " GROUP BY vl.doc_type ORDER BY total DESC";
        return db.sql(sql)
            .map((row, md) -> DocTypeBreakdownDto.builder()
                .docType(row.get("doc_type", String.class))
                .total(row.get("total", Long.class))
                .successCount(row.get("success", Long.class))
                .failureCount(row.get("failure", Long.class))
                .successRate(row.get("success_rate", Double.class))
                .avgProcessingTimeMs(row.get("avg_ms", Double.class))
                .build()).all();
    }

    public Flux<RecentVerificationDto> getRecentVerifications(Long platformId) {
        String whereClause = platformId != null ? "WHERE vl.platform_id = " + platformId : "WHERE 1=1";
        String sql = "SELECT vl.id, vl.platform_id, p.name AS platform_name, vl.date, " +
            "vl.doc_type, vl.status, vl.reason, vl.confidence, vl.processing_time_ms " +
            "FROM verification_logs vl JOIN platforms p ON p.id = vl.platform_id " +
            whereClause + " ORDER BY vl.date DESC LIMIT 10";
        return db.sql(sql)
            .map((row, md) -> RecentVerificationDto.builder()
                .id(row.get("id", Long.class))
                .platformId(row.get("platform_id", Long.class))
                .platformName(row.get("platform_name", String.class))
                .date(row.get("date", java.time.LocalDateTime.class))
                .docType(row.get("doc_type", String.class))
                .status(row.get("status", String.class))
                .reason(row.get("reason", String.class))
                .confidence(row.get("confidence", Double.class))
                .processingTimeMs(row.get("processing_time_ms", Integer.class))
                .build()).all();
    }

    public Flux<DetailedVerificationDto> getSuccessfulVerifications(Long platformId, LocalDateTime from, LocalDateTime to) {
        String whereBase = "WHERE vl.status = 'ACCEPTED'" + platformFilter(platformId) + dateFilter(from, to);
        String sql = "SELECT vl.id, vl.platform_id, p.name AS platform_name, vl.date, " +
            "vl.doc_type, vl.status, vl.confidence, vl.processing_time_ms, " +
            "vl.document_number, vl.holder_name, vl.date_of_birth, vl.issue_date, vl.expiry_date, vl.additional_fields " +
            "FROM verification_logs vl JOIN platforms p ON p.id = vl.platform_id " + whereBase + " ORDER BY vl.date DESC";
        return db.sql(sql)
            .map((row, md) -> DetailedVerificationDto.builder()
                .id(row.get("id", Long.class))
                .platformId(row.get("platform_id", Long.class))
                .platformName(row.get("platform_name", String.class))
                .date(row.get("date", java.time.LocalDateTime.class))
                .docType(row.get("doc_type", String.class))
                .status(row.get("status", String.class))
                .confidence(row.get("confidence", Double.class))
                .processingTimeMs(row.get("processing_time_ms", Integer.class))
                .documentNumber(row.get("document_number", String.class))
                .holderName(row.get("holder_name", String.class))
                .dateOfBirth(row.get("date_of_birth", String.class))
                .issueDate(row.get("issue_date", String.class))
                .expiryDate(row.get("expiry_date", String.class))
                .additionalFields(row.get("additional_fields", String.class))
                .build()).all();
    }

    public Flux<UsageStatisticDto> getOverallUsageStatistics() {
        String sql = "SELECT platform_id, doc_type, COUNT(*) AS total_logs, " +
            "CAST(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS success_rate, " +
            "CAST(SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS failure_rate, " +
            "CAST(AVG(confidence) AS DOUBLE PRECISION) AS avg_confidence, " +
            "STRING_AGG(DISTINCT reason, ', ') AS rejection_reasons FROM verification_logs GROUP BY platform_id, doc_type";
        return db.sql(sql).map((row, md) -> UsageStatisticDto.builder()
            .platformId(row.get("platform_id", Long.class)).docType(row.get("doc_type", String.class))
            .totalLogs(row.get("total_logs", Long.class)).successRate(row.get("success_rate", Double.class))
            .failureRate(row.get("failure_rate", Double.class)).avgConfidence(row.get("avg_confidence", Double.class))
            .rejectionReasons(row.get("rejection_reasons", String.class)).build()).all();
    }

    public Flux<UsageStatisticDto> getUsageStatisticsByPlatform(Long platformId) {
        String sql = "SELECT platform_id, doc_type, COUNT(*) AS total_logs, " +
            "CAST(SUM(CASE WHEN status='ACCEPTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS success_rate, " +
            "CAST(SUM(CASE WHEN status='REJECTED' THEN 1 ELSE 0 END)*100.0/COUNT(*) AS DOUBLE PRECISION) AS failure_rate, " +
            "CAST(AVG(confidence) AS DOUBLE PRECISION) AS avg_confidence, " +
            "STRING_AGG(DISTINCT reason, ', ') AS rejection_reasons " +
            "FROM verification_logs WHERE platform_id = :platformId GROUP BY platform_id, doc_type";
        return db.sql(sql).bind("platformId", platformId).map((row, md) -> UsageStatisticDto.builder()
            .platformId(row.get("platform_id", Long.class)).docType(row.get("doc_type", String.class))
            .totalLogs(row.get("total_logs", Long.class)).successRate(row.get("success_rate", Double.class))
            .failureRate(row.get("failure_rate", Double.class)).avgConfidence(row.get("avg_confidence", Double.class))
            .rejectionReasons(row.get("rejection_reasons", String.class)).build()).all();
    }
}
