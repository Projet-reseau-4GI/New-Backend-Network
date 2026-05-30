package com.projects.application.service.metrics;

import com.projects.application.port.in.metrics.DashboardMetricsUseCase;
import com.projects.adapter.in.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Application use case implementation — Dashboard metrics.
 */
@Service
@RequiredArgsConstructor
public class DashboardMetricsUseCaseImpl implements DashboardMetricsUseCase {

    private final MetricsApplicationService metricsService;

    @Override
    public Mono<DashboardStatsDto> getDashboardStats(Long platformId, LocalDateTime from, LocalDateTime to, String period) {
        return metricsService.getDashboardStats(platformId, from, to, period);
    }

    @Override
    public Flux<TimeSeriesPoint> getVerificationsOverTime(Long platformId, LocalDateTime from, LocalDateTime to, String granularity) {
        return metricsService.getVerificationsOverTime(platformId, from, to, granularity);
    }

    @Override
    public Flux<HourlyTrafficDto> getHourlyTraffic(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getHourlyTraffic(platformId, from, to);
    }

    @Override
    public Flux<StatusDistributionDto> getStatusDistribution(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getStatusDistribution(platformId, from, to);
    }

    @Override
    public Flux<DocTypeBreakdownDto> getDocTypeBreakdown(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getDocTypeBreakdown(platformId, from, to);
    }

    @Override
    public Flux<RecentVerificationDto> getRecentVerifications(Long platformId) {
        return metricsService.getRecentVerifications(platformId);
    }

    @Override
    public Flux<DetailedVerificationDto> getSuccessfulVerifications(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getSuccessfulVerifications(platformId, from, to);
    }

    @Override
    public Flux<UsageStatisticDto> getOverallUsageStatistics() {
        return metricsService.getOverallUsageStatistics();
    }

    @Override
    public Flux<UsageStatisticDto> getUsageStatisticsByPlatform(Long platformId) {
        return metricsService.getUsageStatisticsByPlatform(platformId);
    }
}
