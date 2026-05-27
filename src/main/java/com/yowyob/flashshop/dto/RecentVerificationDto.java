package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for recent verification details.
 * Used for displaying recent activity in dashboards.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentVerificationDto {

    private Long id;
    private Long platform_id;
    private String platform_name;
    private LocalDateTime date;
    private String doc_type;
    private String status;
    private String reason;
    private Double confidence;
    private Integer processing_time_ms;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("platformId")
    public Long getPlatformId() {
        return platform_id;
    }

    @JsonProperty("platformName")
    public String getPlatformName() {
        return platform_name;
    }

    @JsonProperty("docType")
    public String getDocType() {
        return doc_type;
    }

    @JsonProperty("processingTimeMs")
    public Integer getProcessingTimeMs() {
        return processing_time_ms;
    }
}
