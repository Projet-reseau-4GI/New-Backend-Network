package com.projects.service;

import com.projects.dto.DocumentAnalysisResponse;
import com.projects.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ultra-Robust Document Analysis Service for Cameroon Identity Documents.
 * Uses multi-strategy extraction with tokenization, pattern discovery, and
 * intelligent validation to maximize information recovery from noisy OCR text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisService {

    private final DocumentRepository documentRepository;
    private final EnhancedDocumentService enhancedDocumentService;
    private final GeminiService geminiService;
    private final Validator validator;

    // Date patterns commonly found in Cameroon documents
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy")
    };

    public Mono<DocumentAnalysisResponse> analyzeFull(String front, String back) {
        String combined = front + "\n" + back;
        return geminiService.extractData(combined)
                .map(geminiFields -> analyze(front, back, geminiFields));
    }

    private DocumentAnalysisResponse analyze(String front, String back, Map<String, String> geminiFields) {
        String rawCombined = front + "\n" + back;
        log.info("=== Starting Gemini-Only Document Analysis ===");
        log.info("Raw text length: {}", rawCombined.length());

        // Use Gemini fields as primary source
        Map<String, String> fields = new HashMap<>(geminiFields);
        String docType = fields.getOrDefault("documentType", "UNKNOWN");
        String issuingCountry = fields.getOrDefault("issuingCountry", "UNKNOWN");

        // CLEANUP: Remove "null" strings that Gemini might return
        fields.entrySet()
                .removeIf(e -> e.getValue() == null || e.getValue().equalsIgnoreCase("null") || e.getValue().isBlank());

        // PHASE: DATE PARSING
        LocalDate birthDate = parseDate(fields.get("dateOfBirth"));
        LocalDate issueDate = parseDate(fields.get("issueDate"));
        LocalDate expiryDate = parseDate(fields.get("expiryDate"));

        // PHASE: NAME BUILDING
        String holderName = buildHolderName(fields);

        // PHASE: VALIDITY DETERMINATION
        boolean namesValid = fields.get("surname") != null && fields.get("givenNames") != null;
        boolean isExpired = expiryDate != null && expiryDate.isBefore(LocalDate.now());
        boolean hasDocNumber = fields.get("documentNumber") != null;

        // Nomenclature Check
        boolean isNomenclatureValid = validateNomenclature(
                issuingCountry,
                docType,
                fields.get("documentNumber"));

        // Primary user rule: Document is valid if not expired and has basic info AND
        // nomenclature is valid
        boolean valid = !isExpired && namesValid && !docType.equals("UNKNOWN") && hasDocNumber && isNomenclatureValid;

        StringBuilder msg = new StringBuilder();
        if (valid) {
            msg.append("Document valide (Analyse Gemini)");
        } else {
            if (docType.equals("UNKNOWN"))
                msg.append("Type inconnu. ");
            if (!namesValid)
                msg.append("Noms manquants. ");
            if (!hasDocNumber)
                msg.append("Numéro manquant. ");
            else if (!isNomenclatureValid)
                msg.append("Format du numéro invalide pour le pays (").append(issuingCountry).append("). ");
            if (isExpired)
                msg.append("Document expiré. ");
            if (msg.length() == 0)
                msg.append("Document non conforme.");
        }
        String validationMessage = msg.toString().trim();

        // PHASE: CONFIDENCE CALCULATION (Simplified for Gemini)
        double confidence = 0.9; // We trust Gemini
        if (!namesValid || !hasDocNumber)
            confidence = 0.5;
        else if (!isNomenclatureValid)
            confidence = 0.6; // Has number but failed format rules

        log.info("=== Analysis Complete: type={}, country={}, confidence={}, valid={} ===", docType, issuingCountry,
                confidence, valid);

        DocumentAnalysisResponse response = DocumentAnalysisResponse.builder()
                .documentType(docType)
                .issuingCountry(issuingCountry)
                .documentNumber(fields.get("documentNumber"))
                .holderName(holderName)
                .dateOfBirth(birthDate)
                .issueDate(issueDate)
                .expirationDate(expiryDate)
                .isValid(valid)
                .validationMessage(validationMessage)
                .confidenceScore(confidence)
                .hasUncertainty(confidence < 0.6)
                .additionalFields(buildAdditionalFields(fields))
                .rawExtractedText(rawCombined)
                .build();

        // Formal validation
        Set<ConstraintViolation<DocumentAnalysisResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            String formalSummary = violations.stream()
                    .map(v -> v.getMessage())
                    .distinct()
                    .collect(Collectors.joining(", "));
            response.setValidationMessage(response.getValidationMessage() + " (Format: " + formalSummary + ")");
        }

        return response;
    }

    private String buildHolderName(Map<String, String> fields) {
        String s = fields.get("surname"), g = fields.get("givenNames");
        if (s != null && g != null)
            return s.trim() + " " + g.trim();
        return s != null ? s.trim() : (g != null ? g.trim() : "INCONNU");
    }

    private Map<String, String> buildAdditionalFields(Map<String, String> fields) {
        Map<String, String> add = new LinkedHashMap<>();
        // Filter out fields already present in the main response
        Set<String> topLevel = Set.of("surname", "givenNames", "documentNumber", "dateOfBirth", "issueDate",
                "expiryDate", "expirationDate", "documentType", "issuingCountry");
        fields.forEach((k, v) -> {
            if (v != null && !v.isEmpty() && !topLevel.contains(k))
                add.put(k, v);
        });
        return add;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null)
            return null;
        String clean = dateStr.replaceAll("[^\\d./-]", "").trim();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean validateNomenclature(String country, String docType, String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return false;
        }
        if (country == null || country.equalsIgnoreCase("UNKNOWN")) {
            // Cannot strictly validate nomenclature without country, but ensure it exists
            return documentNumber.length() >= 5;
        }

        String normalizedCountry = country.toLowerCase().trim();
        String normalizedNumber = documentNumber.replaceAll("[^a-zA-Z0-9]", ""); // keep only alphanumerics

        // Specific Rules for CEMAC
        if (normalizedCountry.contains("gabon")) {
            // Gabon: NIP is typically 14 alphanumerics
            return normalizedNumber.length() == 14;
        } else if (normalizedCountry.contains("cameroun") || normalizedCountry.contains("cameroon")) {
            // Cameroon standard ranges
            if ("ID_CARD".equals(docType)) {
                return normalizedNumber.length() >= 9 && normalizedNumber.length() <= 17;
            } else if ("PASSPORT".equals(docType)) {
                return normalizedNumber.length() >= 7;
            }
            return normalizedNumber.length() >= 5;
        } else if (normalizedCountry.contains("tchad") || normalizedCountry.contains("chad")) {
            // Tchad: NNI validations (standard robust)
            return normalizedNumber.length() >= 5 && normalizedNumber.length() <= 20;
        } else if (normalizedCountry.contains("congo")) {
            // Congo (RDC or Brazzaville)
            return normalizedNumber.length() >= 5 && normalizedNumber.length() <= 25;
        } else if (normalizedCountry.contains("centrafrique") || normalizedCountry.contains("central african")) {
            // RCA validations
            return normalizedNumber.length() >= 5 && normalizedNumber.length() <= 20;
        }

        // Fallback for other valid string countries
        return documentNumber.length() >= 5 && documentNumber.length() <= 30;
    }
}