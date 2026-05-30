package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for document type breakdown analysis.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocTypeBreakdownDto {

    private String doc_type;
    private Long total;
    private Long success_count;
    private Long failure_count;
    private Double success_rate;
    private Double avg_processing_time_ms;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("docType")
    public String getDocType() {
        return doc_type;
    }

    @JsonProperty("successCount")
    public Long getSuccessCount() {
        return success_count;
    }

    @JsonProperty("failureCount")
    public Long getFailureCount() {
        return failure_count;
    }

    @JsonProperty("successRate")
    public Double getSuccessRate() {
        return success_rate;
    }

    @JsonProperty("avgProcessingTimeMs")
    public Double getAvgProcessingTimeMs() {
        return avg_processing_time_ms;
    }
}
