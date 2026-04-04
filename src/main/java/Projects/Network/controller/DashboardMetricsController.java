package Projects.Network.controller;

import Projects.Network.dto.UsageStatisticDto;
import Projects.Network.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class DashboardMetricsController {

    private final MetricsService metricsService;

    @GetMapping("/usage")
    public Flux<UsageStatisticDto> getOverallUsageStatistics() {
        return metricsService.getOverallUsageStatistics();
    }

    @GetMapping("/platform/{id}")
    public Flux<UsageStatisticDto> getUsageStatisticsByPlatform(@PathVariable Long id) {
        return metricsService.getUsageStatisticsByPlatform(id);
    }
}
