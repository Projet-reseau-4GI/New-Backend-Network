package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for SuperAdmin dashboard statistics.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsDtoSuperAdmin {
    private Long total_users;
    private Long total_verifications;
    private Long successful_verifications;
    private Long failed_verifications;
    private Long pending_verifications;
    private Double avg_processing_time_ms;
    private Long active_api_tokens;
    private Long total_api_tokens_created;

    // Data for charts
    private List<VerificationsOverTimeDto> verifications_trend; // Evolution per day/week/month
    private List<PeakUsageDto> peak_usage; // Traffic per hour
    private Map<String, Long> status_distribution; // Success/Fail/Pending distribution

    private List<AdminVerificationResultDtoSuperAdmin> last_ten_verifications;

    /**
     * DTO for verifications over time trend.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationsOverTimeDto {
        private String label;
        private Long count;
    }

    /**
     * DTO for peak usage statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeakUsageDto {
        private Integer hour;
        private Long count;
    }
}
