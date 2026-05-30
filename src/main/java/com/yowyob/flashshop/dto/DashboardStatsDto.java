package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for general dashboard statistics.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {

    private Long total_verifications;
    private Long success_count;
    private Long failure_count;
    private Long pending_count;
    private Long total_users;
    private Double avg_processing_time_ms;
    private Long total_api_tokens_created;

    /** Period label e.g. "7d", "30d", "90d", "custom" */
    private String period;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("totalVerifications")
    public Long getTotalVerifications() {
        return total_verifications;
    }

    @JsonProperty("successCount")
    public Long getSuccessCount() {
        return success_count;
    }

    @JsonProperty("failureCount")
    public Long getFailureCount() {
        return failure_count;
    }

    @JsonProperty("pendingCount")
    public Long getPendingCount() {
        return pending_count;
    }

    @JsonProperty("totalUsers")
    public Long getTotalUsers() {
        return total_users;
    }

    @JsonProperty("avgProcessingTimeMs")
    public Double getAvgProcessingTimeMs() {
        return avg_processing_time_ms;
    }

    @JsonProperty("totalApiTokensCreated")
    public Long getTotalApiTokensCreated() {
        return total_api_tokens_created;
    }
}
