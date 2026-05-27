package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for usage statistics per platform and document type.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatisticDto {

    private Long platform_id;
    private String doc_type;
    private Long total_logs;
    private Double success_rate;
    private Double failure_rate;
    private Double avg_confidence;
    private String rejection_reasons;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("platformId")
    public Long getPlatformId() {
        return platform_id;
    }

    @JsonProperty("docType")
    public String getDocType() {
        return doc_type;
    }

    @JsonProperty("totalLogs")
    public Long getTotalLogs() {
        return total_logs;
    }

    @JsonProperty("successRate")
    public Double getSuccessRate() {
        return success_rate;
    }

    @JsonProperty("failureRate")
    public Double getFailureRate() {
        return failure_rate;
    }

    @JsonProperty("avgConfidence")
    public Double getAvgConfidence() {
        return avg_confidence;
    }

    @JsonProperty("rejectionReasons")
    public String getRejectionReasons() {
        return rejection_reasons;
    }
}
