package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Breakdown by document type */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocTypeBreakdownDto {
    private String docType;
    private Long total;
    private Long successCount;
    private Long failureCount;
    private Double successRate;
    private Double avgProcessingTimeMs;
}
