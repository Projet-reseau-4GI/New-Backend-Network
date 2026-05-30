package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object representing a single data point in a time-series chart.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {

    /** Label: date string (day/week/month) */
    private String label;
    private Long count;
    private Long success_count;
    private Long failure_count;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("successCount")
    public Long getSuccessCount() {
        return success_count;
    }

    @JsonProperty("failureCount")
    public Long getFailureCount() {
        return failure_count;
    }
}
