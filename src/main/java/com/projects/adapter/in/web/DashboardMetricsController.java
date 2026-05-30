package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.*;
import com.projects.application.port.in.metrics.DashboardMetricsUseCase;
import com.projects.application.service.metrics.DashboardExportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Inbound adapter (Web) — Dashboard metrics and export.
 * Injects DashboardMetricsUseCase port — no direct service coupling.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Dashboard", description = "Statistiques, graphiques et export du tableau de bord")
public class DashboardMetricsController {

    private final DashboardMetricsUseCase metricsUseCase;
    private final DashboardExportApplicationService exportService;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @GetMapping("/stats")
    @Operation(summary = "Indicateurs clés (KPIs)")
    public Mono<DashboardStatsDto> getStats(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getDashboardStats(platformId, range[0], range[1], period);
    }

    @GetMapping("/verifications-over-time")
    @Operation(summary = "Évolution des vérifications")
    public Flux<TimeSeriesPoint> getVerificationsOverTime(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @Parameter(description = "Granularité: day | week | month")
            @RequestParam(defaultValue = "day") String granularity) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getVerificationsOverTime(platformId, range[0], range[1], granularity);
    }

    @GetMapping("/hourly-traffic")
    @Operation(summary = "Trafic par heure")
    public Flux<HourlyTrafficDto> getHourlyTraffic(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getHourlyTraffic(platformId, range[0], range[1]);
    }

    @GetMapping("/status-distribution")
    @Operation(summary = "Répartition succès / échec")
    public Flux<StatusDistributionDto> getStatusDistribution(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getStatusDistribution(platformId, range[0], range[1]);
    }

    @GetMapping("/doc-type-breakdown")
    @Operation(summary = "Distribution par type de document")
    public Flux<DocTypeBreakdownDto> getDocTypeBreakdown(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getDocTypeBreakdown(platformId, range[0], range[1]);
    }

    @GetMapping("/recent-verifications")
    @Operation(summary = "10 dernières vérifications")
    public Flux<RecentVerificationDto> getRecentVerifications(@RequestParam(required = false) Long platformId) {
        return metricsUseCase.getRecentVerifications(platformId);
    }

    @GetMapping("/successful-verifications")
    @Operation(summary = "Vérifications réussies détaillées")
    public Flux<DetailedVerificationDto> getSuccessfulVerifications(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsUseCase.getSuccessfulVerifications(platformId, range[0], range[1]);
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Exporter en CSV")
    public Mono<ResponseEntity<ByteArrayResource>> exportCsv(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        String filename = "verifid-rapport-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        return exportService.exportCsv(platformId, range[0], range[1])
            .map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Exporter en PDF")
    public Mono<ResponseEntity<ByteArrayResource>> exportPdf(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime[] range = resolvePeriod(period, from, to);
        String filename = "verifid-rapport-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".pdf";
        return exportService.exportPdf(platformId, range[0], range[1], period)
            .map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(bytes.length)
                .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    @GetMapping("/verifications/{id}/pdf")
    @Operation(summary = "Rapport PDF d'une vérification spécifique")
    public Mono<ResponseEntity<ByteArrayResource>> exportSinglePdf(@PathVariable Long id) {
        String filename = "verifid-details-" + id + ".pdf";
        return exportService.exportSingleVerificationPdf(id)
            .map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(bytes.length)
                .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    private LocalDateTime[] resolvePeriod(String period, String from, String to) {
        if (from != null && to != null) {
            return new LocalDateTime[]{LocalDateTime.parse(from, ISO), LocalDateTime.parse(to, ISO)};
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = switch (period != null ? period : "30d") {
            case "7d"  -> now.minusDays(7);
            case "90d" -> now.minusDays(90);
            default    -> now.minusDays(30);
        };
        return new LocalDateTime[]{start, now};
    }
}
