package com.projects.application.port.in.metrics;

import com.projects.adapter.in.web.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

/**
 * Inbound port — Dashboard metrics use cases.
 */
public interface DashboardMetricsUseCase {
    Mono<DashboardStatsDto> getDashboardStats(Long platformId, LocalDateTime from, LocalDateTime to, String period);
    Flux<TimeSeriesPoint> getVerificationsOverTime(Long platformId, LocalDateTime from, LocalDateTime to, String granularity);
    Flux<HourlyTrafficDto> getHourlyTraffic(Long platformId, LocalDateTime from, LocalDateTime to);
    Flux<StatusDistributionDto> getStatusDistribution(Long platformId, LocalDateTime from, LocalDateTime to);
    Flux<DocTypeBreakdownDto> getDocTypeBreakdown(Long platformId, LocalDateTime from, LocalDateTime to);
    Flux<RecentVerificationDto> getRecentVerifications(Long platformId);
    Flux<DetailedVerificationDto> getSuccessfulVerifications(Long platformId, LocalDateTime from, LocalDateTime to);
    Flux<UsageStatisticDto> getOverallUsageStatistics();
    Flux<UsageStatisticDto> getUsageStatisticsByPlatform(Long platformId);
}
