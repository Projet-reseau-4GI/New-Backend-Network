package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatisticDto {
    private Long platformId;
    private String docType;
    private Long totalLogs;
    private Double successRate;
    private Double failureRate;
    private Double avgConfidence;
    private String rejectionReasons;
}
