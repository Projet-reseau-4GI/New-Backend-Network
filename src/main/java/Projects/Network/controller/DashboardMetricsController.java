package Projects.Network.controller;

import Projects.Network.dto.*;
import Projects.Network.service.DashboardExportService;
import Projects.Network.service.MetricsService;
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

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Dashboard", description = "Statistiques, graphiques et export du tableau de bord")
public class DashboardMetricsController {

    private final MetricsService metricsService;
    private final DashboardExportService exportService;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. KPI STATS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Indicateurs clés (KPIs)", description = "Retourne total vérifications, succès, échecs, temps moyen, tokens API actifs.")
    public Mono<DashboardStatsDto> getStats(
            @Parameter(description = "ID de plateforme (optionnel, admin seulement)") @RequestParam(required = false) Long platformId,
            @Parameter(description = "Période prédéfinie: 7d | 30d | 90d (ignoré si from/to fournis)") @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getDashboardStats(platformId, range[0], range[1], period);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VERIFICATIONS OVER TIME
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/verifications-over-time")
    @Operation(summary = "Évolution des vérifications", description = "Données pour le graphique linéaire (par jour/semaine/mois).")
    public Flux<TimeSeriesPoint> getVerificationsOverTime(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @Parameter(description = "Granularité: day | week | month") @RequestParam(defaultValue = "day") String granularity) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getVerificationsOverTime(platformId, range[0], range[1], granularity);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. HOURLY TRAFFIC
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/hourly-traffic")
    @Operation(summary = "Trafic par heure", description = "Nombre de vérifications par heure de la journée (0–23) pour le graphique en barres.")
    public Flux<HourlyTrafficDto> getHourlyTraffic(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getHourlyTraffic(platformId, range[0], range[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. STATUS DISTRIBUTION
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/status-distribution")
    @Operation(summary = "Répartition succès / échec", description = "Données pour le graphique circulaire (ACCEPTED / REJECTED).")
    public Flux<StatusDistributionDto> getStatusDistribution(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getStatusDistribution(platformId, range[0], range[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. DOC TYPE BREAKDOWN
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/doc-type-breakdown")
    @Operation(summary = "Distribution par type de document", description = "Totaux et taux de succès par type de document.")
    public Flux<DocTypeBreakdownDto> getDocTypeBreakdown(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getDocTypeBreakdown(platformId, range[0], range[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RECENT VERIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/recent-verifications")
    @Operation(summary = "10 dernières vérifications", description = "Liste des 10 vérifications les plus récentes avec tous leurs détails.")
    public Flux<RecentVerificationDto> getRecentVerifications(
            @RequestParam(required = false) Long platformId) {
        return metricsService.getRecentVerifications(platformId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6.5 DETAILED SUCCESSFUL VERIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/successful-verifications")
    @Operation(summary = "Consultation détaillée des résultats", description = "Liste l'ensemble des vérifications abouties avec les données extraites.")
    public Flux<DetailedVerificationDto> getSuccessfulVerifications(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        return metricsService.getSuccessfulVerifications(platformId, range[0], range[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. EXPORT CSV
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/export/csv")
    @Operation(summary = "Exporter en CSV", description = "Télécharge un fichier CSV des vérifications récentes (compatible Excel).")
    public Mono<ResponseEntity<ByteArrayResource>> exportCsv(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        String filename = "verifid-rapport-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + ".csv";

        return exportService.exportCsv(platformId, range[0], range[1])
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(filename).build().toString())
                        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                        .contentLength(bytes.length)
                        .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. EXPORT PDF
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/export/pdf")
    @Operation(summary = "Exporter en PDF", description = "Génère un rapport PDF professionnel multi-sections.")
    public Mono<ResponseEntity<ByteArrayResource>> exportPdf(
            @RequestParam(required = false) Long platformId,
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime[] range = resolvePeriod(period, from, to);
        String filename = "verifid-rapport-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + ".pdf";

        return exportService.exportPdf(platformId, range[0], range[1], period)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(filename).build().toString())
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(bytes.length)
                        .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. EXPORT SINGLE PDF
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/verifications/{id}/pdf")
    @Operation(summary = "Exporter un rapport PDF spécifique", description = "Génère un rapport détaillé pour une vérification spécifique.")
    public Mono<ResponseEntity<ByteArrayResource>> exportSinglePdf(@PathVariable Long id) {
        String filename = "verifid-details-" + id + ".pdf";
        return exportService.exportSingleVerificationPdf(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(filename).build().toString())
                        .contentType(MediaType.APPLICATION_PDF)
                        .contentLength(bytes.length)
                        .<ByteArrayResource>body(new ByteArrayResource(bytes)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER — resolve period string to [from, to] date range
    // ─────────────────────────────────────────────────────────────────────────

    private LocalDateTime[] resolvePeriod(String period, String from, String to) {
        // Custom range takes priority
        if (from != null && to != null) {
            return new LocalDateTime[] {
                    LocalDateTime.parse(from, ISO),
                    LocalDateTime.parse(to, ISO)
            };
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = switch (period != null ? period : "30d") {
            case "7d" -> now.minusDays(7);
            case "90d" -> now.minusDays(90);
            default -> now.minusDays(30); // "30d"
        };
        return new LocalDateTime[] { start, now };
    }
}
