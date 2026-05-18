package Projects.Network.service;

import Projects.Network.dto.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service responsible for generating professional CSV and PDF exports
 * of the VerifID dashboard data.
 */
import Projects.Network.repository.VerificationLogRepository;
import Projects.Network.model.VerificationLog;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardExportService {

    private final MetricsService metricsService;
    private final VerificationLogRepository verificationLogRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    // CSV EXPORT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a CSV byte array containing all recent verification logs.
     * Columns: ID, Date, Type de document, Statut, Confiance (%), Temps (ms),
     * Raison du rejet
     */
    public Mono<byte[]> exportCsv(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getRecentVerifications(platformId)
                .collectList()
                .flatMap(rows -> {
                    StringBuilder sb = new StringBuilder();
                    // BOM for Excel UTF-8 compatibility
                    sb.append('\uFEFF');
                    // Header
                    sb.append(
                            "ID,Plateforme,Date,Type de document,Statut,Confiance (%),Temps de traitement (ms),Raison du rejet\n");
                    // Rows
                    for (RecentVerificationDto r : rows) {
                        sb.append(csv(r.getId()))
                                .append(',').append(csv(r.getPlatformName()))
                                .append(',').append(csv(r.getDate() != null ? r.getDate().format(DATE_LABEL) : ""))
                                .append(',').append(csv(r.getDocType()))
                                .append(',').append(csv(r.getStatus()))
                                .append(',').append(r.getConfidence() != null
                                        ? String.format("%.2f", r.getConfidence() * 100)
                                        : "")
                                .append(',').append(r.getProcessingTimeMs() != null ? r.getProcessingTimeMs() : "")
                                .append(',').append(csv(r.getReason()))
                                .append('\n');
                    }
                    return Mono.just(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                });
    }

    private String csv(Object val) {
        if (val == null)
            return "";
        String s = val.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF EXPORT
    // ─────────────────────────────────────────────────────────────────────────

    private static final Color COLOR_PRIMARY = new Color(26, 35, 126); // #1a237e deep blue
    private static final Color COLOR_SECONDARY = new Color(41, 98, 255); // #2962ff
    private static final Color COLOR_ACCEPTED = new Color(27, 128, 77); // green
    private static final Color COLOR_REJECTED = new Color(183, 28, 28); // red
    private static final Color COLOR_ROW_EVEN = new Color(240, 244, 255); // light blue tint
    private static final Color COLOR_HEADER_BG = new Color(26, 35, 126); // same as primary
    private static final Color COLOR_BORDER = new Color(200, 210, 240);

    /**
     * Generates a professional multi-section PDF report.
     * Sections:
     * 1. Cover header (logo placeholder + title + generation date)
     * 2. KPI summary cards
     * 3. Status distribution table
     * 4. Document type breakdown table
     * 5. Recent verifications table (last 10)
     * 6. Footer with page numbers
     */
    public Mono<byte[]> exportPdf(Long platformId, LocalDateTime from, LocalDateTime to, String period) {
        return Mono.zip(
                metricsService.getDashboardStats(platformId, from, to, period),
                metricsService.getStatusDistribution(platformId, from, to).collectList(),
                metricsService.getDocTypeBreakdown(platformId, from, to).collectList(),
                metricsService.getRecentVerifications(platformId).collectList())
                .map(tuple -> buildPdf(tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), period));
    }

    private byte[] buildPdf(
            DashboardStatsDto stats,
            List<StatusDistributionDto> distribution,
            List<DocTypeBreakdownDto> docTypes,
            List<RecentVerificationDto> recents,
            String period) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4, 36, 36, 60, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Page event for header/footer on every page
        writer.setPageEvent(new HeaderFooterPageEvent());

        doc.open();

        // ── 1. COVER HEADER ─────────────────────────────────────────────────
        addCoverHeader(doc, period);

        // ── 2. KPI SUMMARY ──────────────────────────────────────────────────
        addSectionTitle(doc, "Indicateurs clés de performance");
        addKpiTable(doc, stats);

        doc.add(Chunk.NEWLINE);

        // ── 3. STATUS DISTRIBUTION ──────────────────────────────────────────
        addSectionTitle(doc, "Répartition par statut");
        addStatusTable(doc, distribution);

        doc.add(Chunk.NEWLINE);

        // ── 4. DOC TYPE BREAKDOWN ────────────────────────────────────────────
        addSectionTitle(doc, "Analyse par type de document");
        addDocTypeTable(doc, docTypes);

        doc.add(Chunk.NEWLINE);

        // ── 5. RECENT VERIFICATIONS ──────────────────────────────────────────
        addSectionTitle(doc, "10 dernières vérifications");
        addRecentsTable(doc, recents);

        doc.close();

        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SINGLE VERIFICATION PDF EXPORT
    // ─────────────────────────────────────────────────────────────────────────
    public Mono<byte[]> exportSingleVerificationPdf(Long verificationId) {
        return verificationLogRepository.findById(verificationId)
                .map(log -> {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Document doc = new Document(PageSize.A4, 36, 36, 60, 50);
                    PdfWriter writer = PdfWriter.getInstance(doc, baos);
                    writer.setPageEvent(new HeaderFooterPageEvent());
                    doc.open();

                    try {
                        addCoverHeader(doc, "Rapport Spécifique");

                        addSectionTitle(doc, "Détails de la Vérification #" + log.getId());

                        PdfPTable table = new PdfPTable(2);
                        table.setWidthPercentage(100);
                        table.setWidths(new float[] { 1.5f, 3f });

                        addDetailRow(table, "Statut", log.getStatus());
                        addDetailRow(table, "Type", log.getDocType());
                        addDetailRow(table, "Confiance",
                                log.getConfidence() != null ? String.format("%.1f%%", log.getConfidence() * 100)
                                        : "N/A");
                        addDetailRow(table, "Temps de traitement (ms)",
                                log.getProcessingTimeMs() != null ? log.getProcessingTimeMs() + " ms" : "N/A");

                        addDetailRow(table, "Numéro de document", log.getDocumentNumber());
                        addDetailRow(table, "Nom du titulaire", log.getHolderName());
                        addDetailRow(table, "Date de naissance", log.getDateOfBirth());
                        addDetailRow(table, "Date d'émission", log.getIssueDate());
                        addDetailRow(table, "Date d'expiration", log.getExpiryDate());

                        if (log.getAdditionalFields() != null) {
                            addDetailRow(table, "Champs Additionnels", log.getAdditionalFields());
                        }

                        doc.add(table);

                        if (log.getReason() != null) {
                            doc.add(Chunk.NEWLINE);
                            addSectionTitle(doc, "Raison du rejet");
                            Paragraph reasonP = new Paragraph(log.getReason(),
                                    FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY));
                            doc.add(reasonP);
                        }

                    } catch (DocumentException e) {
                        e.printStackTrace();
                    } finally {
                        doc.close();
                    }
                    return baos.toByteArray();
                });
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
        Font fValue = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        PdfPCell cLabel = new PdfPCell(new Phrase(label, fLabel));
        PdfPCell cValue = new PdfPCell(new Phrase(value != null ? value : "N/A", fValue));
        cLabel.setPadding(8);
        cValue.setPadding(8);
        cLabel.setBorderColor(COLOR_BORDER);
        cValue.setBorderColor(COLOR_BORDER);
        table.addCell(cLabel);
        table.addCell(cValue);
    }

    // ─── Cover header ────────────────────────────────────────────────────────

    private void addCoverHeader(Document doc, String period) throws DocumentException {
        // Blue banner
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_PRIMARY);
        cell.setPadding(18);
        cell.setBorder(Rectangle.NO_BORDER);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.WHITE);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(160, 180, 255));

        Paragraph title = new Paragraph("VerifID — Rapport d'analyse", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);

        String periodLabel = period != null ? switch (period) {
            case "7d" -> "7 derniers jours";
            case "30d" -> "30 derniers jours";
            case "90d" -> "90 derniers jours";
            default -> "Période personnalisée";
        } : "Toutes périodes";

        Paragraph sub = new Paragraph(
                "Généré le " + LocalDateTime.now().format(DTF) + "   •   Période : " + periodLabel,
                subFont);
        sub.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(title);
        cell.addElement(sub);
        banner.addCell(cell);
        doc.add(banner);
        doc.add(Chunk.NEWLINE);
    }

    // ─── Section title ───────────────────────────────────────────────────────

    private void addSectionTitle(Document doc, String text) throws DocumentException {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, COLOR_PRIMARY);
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);

        // Underline separator via a thin table
        PdfPTable sep = new PdfPTable(1);
        sep.setWidthPercentage(100);
        sep.setSpacingBefore(0);
        sep.setSpacingAfter(6);
        PdfPCell sepCell = new PdfPCell();
        sepCell.setFixedHeight(2f);
        sepCell.setBackgroundColor(COLOR_SECONDARY);
        sepCell.setBorder(Rectangle.NO_BORDER);
        sep.addCell(sepCell);
        doc.add(p);
        doc.add(sep);
        doc.add(Chunk.NEWLINE);
    }

    // ─── KPI table (2×3 grid) ────────────────────────────────────────────────

    private void addKpiTable(Document doc, DashboardStatsDto stats) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(8);

        addKpiCell(table, "Total vérifications",
                String.valueOf(stats.getTotalVerifications()), COLOR_PRIMARY);
        addKpiCell(table, "Acceptées",
                String.valueOf(stats.getSuccessCount()), COLOR_ACCEPTED);
        addKpiCell(table, "Rejetées",
                String.valueOf(stats.getFailureCount()), COLOR_REJECTED);
        addKpiCell(table, "Temps moyen (ms)",
                stats.getAvgProcessingTimeMs() != null
                        ? String.format("%.0f ms", stats.getAvgProcessingTimeMs())
                        : "N/A",
                COLOR_SECONDARY);
        addKpiCell(table, "Tokens API actifs",
                String.valueOf(stats.getTotalApiTokensCreated()), COLOR_PRIMARY);
        addKpiCell(table, "Taux de succès",
                stats.getTotalVerifications() != null && stats.getTotalVerifications() > 0
                        ? String.format("%.1f%%",
                                (stats.getSuccessCount() * 100.0) / stats.getTotalVerifications())
                        : "0%",
                COLOR_ACCEPTED);

        doc.add(table);
    }

    private void addKpiCell(PdfPTable table, String label, String value, Color accentColor) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(12);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(1);
        cell.setBackgroundColor(Color.WHITE);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 100, 120));
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, accentColor);

        cell.addElement(new Paragraph(label, labelFont));
        Paragraph val = new Paragraph(value, valueFont);
        val.setSpacingBefore(4);
        cell.addElement(val);
        table.addCell(cell);
    }

    // ─── Status distribution table ───────────────────────────────────────────

    private void addStatusTable(Document doc, List<StatusDistributionDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(60);
        table.setWidths(new float[] { 3f, 2f, 2f });
        table.setHorizontalAlignment(Element.ALIGN_LEFT);

        addTableHeader(table, new String[] { "Statut", "Nombre", "Pourcentage" });

        boolean even = false;
        for (StatusDistributionDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Color statusColor = "ACCEPTED".equals(r.getStatus()) ? COLOR_ACCEPTED : COLOR_REJECTED;
            String label = "ACCEPTED".equals(r.getStatus()) ? "✔ Accepté" : "✘ Rejeté";

            addCell(table, label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, statusColor), bg);
            addCell(table, String.valueOf(r.getCount()),
                    FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY), bg);
            addCell(table,
                    r.getPercentage() != null ? String.format("%.1f%%", r.getPercentage()) : "-",
                    FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY), bg);
            even = !even;
        }
        doc.add(table);
    }

    // ─── Doc type breakdown table ─────────────────────────────────────────────

    private void addDocTypeTable(Document doc, List<DocTypeBreakdownDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 3f, 1.5f, 1.5f, 1.5f, 2f });

        addTableHeader(table, new String[] {
                "Type de document", "Total", "Acceptés", "Rejetés", "Taux de succès" });

        boolean even = false;
        for (DocTypeBreakdownDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Font base = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font name = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY);

            addCell(table, r.getDocType(), name, bg);
            addCell(table, String.valueOf(r.getTotal()), base, bg);
            addCell(table, String.valueOf(r.getSuccessCount()), base, bg);
            addCell(table, String.valueOf(r.getFailureCount()), base, bg);
            double rate = r.getSuccessRate() != null ? r.getSuccessRate() : 0;
            Color rateColor = rate >= 75 ? COLOR_ACCEPTED : rate >= 50 ? new Color(200, 130, 0) : COLOR_REJECTED;
            addCell(table, String.format("%.1f%%", rate),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, rateColor), bg);
            even = !even;
        }
        doc.add(table);
    }

    // ─── Recent verifications table ───────────────────────────────────────────

    private void addRecentsTable(Document doc, List<RecentVerificationDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 2.5f, 2.5f, 2f, 1.5f, 1.5f });

        addTableHeader(table, new String[] {
                "Date & Heure", "Type de document", "Statut", "Confiance", "Temps (ms)" });

        boolean even = false;
        for (RecentVerificationDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Font base = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            boolean accepted = "ACCEPTED".equals(r.getStatus());
            Color statusColor = accepted ? COLOR_ACCEPTED : COLOR_REJECTED;
            String statusLabel = accepted ? "✔ Accepté" : "✘ Rejeté";

            addCell(table,
                    r.getDate() != null ? r.getDate().format(DATE_LABEL) : "-", base, bg);
            addCell(table, r.getDocType() != null ? r.getDocType() : "-", base, bg);
            addCell(table, statusLabel,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, statusColor), bg);
            addCell(table, r.getConfidence() != null
                    ? String.format("%.1f%%", r.getConfidence() * 100)
                    : "-", base, bg);
            addCell(table, r.getProcessingTimeMs() != null
                    ? r.getProcessingTimeMs() + " ms"
                    : "-", base, bg);
            even = !even;
        }
        doc.add(table);
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    private void addTableHeader(PdfPTable table, String[] headers) {
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(COLOR_HEADER_BG);
            cell.setPadding(7);
            cell.setBorderColor(COLOR_BORDER);
            cell.setBorderWidth(0.5f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    // ─── Header/Footer page event ─────────────────────────────────────────────

    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(150, 150, 170));

            // Left: branding
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("VerifID — Rapport confidentiel", footerFont),
                    document.leftMargin(), document.bottomMargin() - 10, 0);

            // Right: page number
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), footerFont),
                    document.right(), document.bottomMargin() - 10, 0);
        }
    }
}
