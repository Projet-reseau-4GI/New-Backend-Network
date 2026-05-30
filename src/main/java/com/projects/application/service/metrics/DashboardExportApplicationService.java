package com.projects.application.service.metrics;

import com.projects.adapter.in.web.dto.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.projects.application.port.out.VerificationLogRepositoryPort;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Application service — PDF/CSV export of dashboard data.
 * Used by AdminDashboardUseCaseImpl and DashboardMetricsController.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardExportApplicationService {

    private final MetricsApplicationService metricsService;
    private final VerificationLogRepositoryPort verificationLogRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color COLOR_PRIMARY   = new Color(26, 35, 126);
    private static final Color COLOR_SECONDARY = new Color(41, 98, 255);
    private static final Color COLOR_ACCEPTED  = new Color(27, 128, 77);
    private static final Color COLOR_REJECTED  = new Color(183, 28, 28);
    private static final Color COLOR_ROW_EVEN  = new Color(240, 244, 255);
    private static final Color COLOR_HEADER_BG = new Color(26, 35, 126);
    private static final Color COLOR_BORDER    = new Color(200, 210, 240);

    public Mono<byte[]> exportCsv(Long platformId, LocalDateTime from, LocalDateTime to) {
        return metricsService.getRecentVerifications(platformId).collectList().flatMap(rows -> {
            StringBuilder sb = new StringBuilder();
            sb.append('\uFEFF');
            sb.append("ID,Plateforme,Date,Type de document,Statut,Confiance (%),Temps (ms),Raison\n");
            for (RecentVerificationDto r : rows) {
                sb.append(csv(r.getId())).append(',').append(csv(r.getPlatformName()))
                  .append(',').append(csv(r.getDate() != null ? r.getDate().format(DATE_LABEL) : ""))
                  .append(',').append(csv(r.getDocType())).append(',').append(csv(r.getStatus()))
                  .append(',').append(r.getConfidence() != null ? String.format("%.2f", r.getConfidence() * 100) : "")
                  .append(',').append(r.getProcessingTimeMs() != null ? r.getProcessingTimeMs() : "")
                  .append(',').append(csv(r.getReason())).append('\n');
            }
            return Mono.just(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
    }

    private String csv(Object val) {
        if (val == null) return "";
        String s = val.toString().replace("\"", "\"\"");
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s + "\"" : s;
    }

    public Mono<byte[]> exportPdf(Long platformId, LocalDateTime from, LocalDateTime to, String period) {
        return Mono.zip(
            metricsService.getDashboardStats(platformId, from, to, period),
            metricsService.getStatusDistribution(platformId, from, to).collectList(),
            metricsService.getDocTypeBreakdown(platformId, from, to).collectList(),
            metricsService.getRecentVerifications(platformId).collectList()
        ).map(t -> buildPdf(t.getT1(), t.getT2(), t.getT3(), t.getT4(), period));
    }

    private byte[] buildPdf(DashboardStatsDto stats, List<StatusDistributionDto> dist,
                             List<DocTypeBreakdownDto> docTypes, List<RecentVerificationDto> recents, String period) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 60, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new HeaderFooterPageEvent());
        doc.open();
        try {
            addCoverHeader(doc, period);
            addSectionTitle(doc, "Indicateurs clés de performance"); addKpiTable(doc, stats);
            doc.add(Chunk.NEWLINE);
            addSectionTitle(doc, "Répartition par statut"); addStatusTable(doc, dist);
            doc.add(Chunk.NEWLINE);
            addSectionTitle(doc, "Analyse par type de document"); addDocTypeTable(doc, docTypes);
            doc.add(Chunk.NEWLINE);
            addSectionTitle(doc, "10 dernières vérifications"); addRecentsTable(doc, recents);
        } catch (DocumentException e) { log.error("PDF generation error", e); }
        doc.close();
        return baos.toByteArray();
    }

    public Mono<byte[]> exportSingleVerificationPdf(Long id) {
        return verificationLogRepository.findById(id).map(vLog -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 36, 36, 60, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new HeaderFooterPageEvent());
            doc.open();
            try {
                addCoverHeader(doc, "Rapport Spécifique");
                addSectionTitle(doc, "Détails de la Vérification #" + vLog.getId());
                PdfPTable table = new PdfPTable(2);
                table.setWidthPercentage(100); table.setWidths(new float[]{1.5f, 3f});
                addDetailRow(table, "Statut", vLog.getStatus());
                addDetailRow(table, "Type", vLog.getDocType());
                addDetailRow(table, "Confiance", vLog.getConfidence() != null ? String.format("%.1f%%", vLog.getConfidence() * 100) : "N/A");
                addDetailRow(table, "Temps (ms)", vLog.getProcessingTimeMs() != null ? vLog.getProcessingTimeMs() + " ms" : "N/A");
                addDetailRow(table, "Numéro de document", vLog.getDocumentNumber());
                addDetailRow(table, "Nom du titulaire", vLog.getHolderName());
                addDetailRow(table, "Date de naissance", vLog.getDateOfBirth());
                addDetailRow(table, "Date d'émission", vLog.getIssueDate());
                addDetailRow(table, "Date d'expiration", vLog.getExpiryDate());
                if (vLog.getAdditionalFields() != null) addDetailRow(table, "Champs additionnels", vLog.getAdditionalFields());
                doc.add(table);
                if (vLog.getReason() != null) {
                    doc.add(Chunk.NEWLINE);
                    addSectionTitle(doc, "Raison du rejet");
                    doc.add(new Paragraph(vLog.getReason(), FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY)));
                }
            } catch (DocumentException e) { log.error("PDF generation error", e); } finally { doc.close(); }
            return baos.toByteArray();
        });
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        Font fL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
        Font fV = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        PdfPCell cL = new PdfPCell(new Phrase(label, fL)); cL.setPadding(8); cL.setBorderColor(COLOR_BORDER);
        PdfPCell cV = new PdfPCell(new Phrase(value != null ? value : "N/A", fV)); cV.setPadding(8); cV.setBorderColor(COLOR_BORDER);
        table.addCell(cL); table.addCell(cV);
    }

    private void addCoverHeader(Document doc, String period) throws DocumentException {
        PdfPTable banner = new PdfPTable(1); banner.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(); cell.setBackgroundColor(COLOR_PRIMARY); cell.setPadding(18); cell.setBorder(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph("VerifID — Rapport d'analyse", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.WHITE));
        title.setAlignment(Element.ALIGN_CENTER);
        String pl = period != null ? switch (period) { case "7d" -> "7 derniers jours"; case "30d" -> "30 derniers jours"; case "90d" -> "90 derniers jours"; default -> "Période personnalisée"; } : "Toutes périodes";
        Paragraph sub = new Paragraph("Généré le " + LocalDateTime.now().format(DTF) + "   •   Période : " + pl, FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(160, 180, 255)));
        sub.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(title); cell.addElement(sub); banner.addCell(cell);
        doc.add(banner); doc.add(Chunk.NEWLINE);
    }

    private void addSectionTitle(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, COLOR_PRIMARY));
        p.setSpacingBefore(10); p.setSpacingAfter(6);
        PdfPTable sep = new PdfPTable(1); sep.setWidthPercentage(100); sep.setSpacingAfter(6);
        PdfPCell sepCell = new PdfPCell(); sepCell.setFixedHeight(2f); sepCell.setBackgroundColor(COLOR_SECONDARY); sepCell.setBorder(Rectangle.NO_BORDER);
        sep.addCell(sepCell); doc.add(p); doc.add(sep); doc.add(Chunk.NEWLINE);
    }

    private void addKpiTable(Document doc, DashboardStatsDto stats) throws DocumentException {
        PdfPTable table = new PdfPTable(3); table.setWidthPercentage(100); table.setSpacingAfter(8);
        addKpiCell(table, "Total vérifications", String.valueOf(stats.getTotalVerifications()), COLOR_PRIMARY);
        addKpiCell(table, "Acceptées", String.valueOf(stats.getSuccessCount()), COLOR_ACCEPTED);
        addKpiCell(table, "Rejetées", String.valueOf(stats.getFailureCount()), COLOR_REJECTED);
        addKpiCell(table, "Temps moyen (ms)", stats.getAvgProcessingTimeMs() != null ? String.format("%.0f ms", stats.getAvgProcessingTimeMs()) : "N/A", COLOR_SECONDARY);
        addKpiCell(table, "Tokens API actifs", String.valueOf(stats.getTotalApiTokensCreated()), COLOR_PRIMARY);
        addKpiCell(table, "Taux de succès", stats.getTotalVerifications() != null && stats.getTotalVerifications() > 0 ? String.format("%.1f%%", (stats.getSuccessCount() * 100.0) / stats.getTotalVerifications()) : "0%", COLOR_ACCEPTED);
        doc.add(table);
    }

    private void addKpiCell(PdfPTable table, String label, String value, Color accent) {
        PdfPCell cell = new PdfPCell(); cell.setPadding(12); cell.setBorderColor(COLOR_BORDER); cell.setBorderWidth(1); cell.setBackgroundColor(Color.WHITE);
        cell.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 100, 120))));
        Paragraph val = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, accent)); val.setSpacingBefore(4); cell.addElement(val);
        table.addCell(cell);
    }

    private void addStatusTable(Document doc, List<StatusDistributionDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(3); table.setWidthPercentage(60); table.setWidths(new float[]{3f, 2f, 2f}); table.setHorizontalAlignment(Element.ALIGN_LEFT);
        addTableHeader(table, new String[]{"Statut", "Nombre", "Pourcentage"});
        boolean even = false;
        for (StatusDistributionDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Color sc = "ACCEPTED".equals(r.getStatus()) ? COLOR_ACCEPTED : COLOR_REJECTED;
            addCell(table, "ACCEPTED".equals(r.getStatus()) ? "✔ Accepté" : "✘ Rejeté", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, sc), bg);
            addCell(table, String.valueOf(r.getCount()), FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY), bg);
            addCell(table, r.getPercentage() != null ? String.format("%.1f%%", r.getPercentage()) : "-", FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY), bg);
            even = !even;
        }
        doc.add(table);
    }

    private void addDocTypeTable(Document doc, List<DocTypeBreakdownDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(5); table.setWidthPercentage(100); table.setWidths(new float[]{3f, 1.5f, 1.5f, 1.5f, 2f});
        addTableHeader(table, new String[]{"Type de document", "Total", "Acceptés", "Rejetés", "Taux"});
        boolean even = false;
        for (DocTypeBreakdownDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Font base = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            addCell(table, r.getDocType(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY), bg);
            addCell(table, String.valueOf(r.getTotal()), base, bg);
            addCell(table, String.valueOf(r.getSuccessCount()), base, bg);
            addCell(table, String.valueOf(r.getFailureCount()), base, bg);
            double rate = r.getSuccessRate() != null ? r.getSuccessRate() : 0;
            Color rc = rate >= 75 ? COLOR_ACCEPTED : rate >= 50 ? new Color(200, 130, 0) : COLOR_REJECTED;
            addCell(table, String.format("%.1f%%", rate), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, rc), bg);
            even = !even;
        }
        doc.add(table);
    }

    private void addRecentsTable(Document doc, List<RecentVerificationDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(5); table.setWidthPercentage(100); table.setWidths(new float[]{2.5f, 2.5f, 2f, 1.5f, 1.5f});
        addTableHeader(table, new String[]{"Date & Heure", "Type de document", "Statut", "Confiance", "Temps (ms)"});
        boolean even = false;
        for (RecentVerificationDto r : rows) {
            Color bg = even ? COLOR_ROW_EVEN : Color.WHITE;
            Font base = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
            boolean acc = "ACCEPTED".equals(r.getStatus());
            addCell(table, r.getDate() != null ? r.getDate().format(DATE_LABEL) : "-", base, bg);
            addCell(table, r.getDocType() != null ? r.getDocType() : "-", base, bg);
            addCell(table, acc ? "✔ Accepté" : "✘ Rejeté", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, acc ? COLOR_ACCEPTED : COLOR_REJECTED), bg);
            addCell(table, r.getConfidence() != null ? String.format("%.1f%%", r.getConfidence() * 100) : "-", base, bg);
            addCell(table, r.getProcessingTimeMs() != null ? r.getProcessingTimeMs() + " ms" : "-", base, bg);
            even = !even;
        }
        doc.add(table);
    }

    private void addTableHeader(PdfPTable table, String[] headers) {
        Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont)); cell.setBackgroundColor(COLOR_HEADER_BG);
            cell.setPadding(7); cell.setBorderColor(COLOR_BORDER); cell.setBorderWidth(0.5f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT); table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bg); cell.setPadding(6); cell.setBorderColor(COLOR_BORDER); cell.setBorderWidth(0.5f);
        table.addCell(cell);
    }

    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(150, 150, 170));
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("VerifID — Rapport confidentiel", f), document.leftMargin(), document.bottomMargin() - 10, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), f), document.right(), document.bottomMargin() - 10, 0);
        }
    }
}
