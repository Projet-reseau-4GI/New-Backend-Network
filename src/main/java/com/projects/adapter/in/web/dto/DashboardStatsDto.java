package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private Long totalVerifications;
    private Long successCount;
    private Long failureCount;
    private Long pendingCount;
    private Long totalUsers;
    private Double avgProcessingTimeMs;
    private Long totalApiTokensCreated;
    /** Period label e.g. "7d", "30d", "90d", "custom" */
    private String period;
}
