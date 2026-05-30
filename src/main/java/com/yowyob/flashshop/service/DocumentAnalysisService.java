package com.yowyob.flashshop.service;

import com.yowyob.flashshop.dto.DocumentAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ultra-Robust Document Analysis Service for Cameroon Identity Documents.
 * Uses multi-strategy extraction with tokenization, pattern discovery, and
 * intelligent validation to maximize information recovery from noisy OCR text.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisService {

    private final GeminiService gemini_service;
    private final Validator validator_instance;

    // Date patterns commonly found in Cameroon documents
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy")
    };

    /**
     * Performs a full analysis of front and back document images.
     *
     * @param front the text extracted from the front side
     * @param back  the text extracted from the back side
     * @return a Mono containing the analysis response
     */
    public Mono<DocumentAnalysisResponse> analyzeFull(String front, String back) {
        String combined = front + "\n" + back;
        return gemini_service.extractData(combined)
                .map(gemini_fields -> analyze(front, back, gemini_fields));
    }

    /**
     * Analyzes document text using Gemini fields as primary data source.
     *
     * @param front         the text from the front
     * @param back          the text from the back
     * @param gemini_fields the fields extracted by Gemini
     * @return the document analysis response
     */
    private DocumentAnalysisResponse analyze(String front, String back, Map<String, String> gemini_fields) {
        String raw_combined = front + "\n" + back;
        log.info("=== Starting Gemini-Only Document Analysis ===");
        log.info("Raw text length: {}", raw_combined.length());

        // Use Gemini fields as primary source
        Map<String, String> fields = new HashMap<>(gemini_fields);
        String doc_type = fields.getOrDefault("documentType", "UNKNOWN");
        String issuing_country = fields.getOrDefault("issuingCountry", "UNKNOWN");

        // CLEANUP: Remove "null" strings that Gemini might return
        fields.entrySet()
                .removeIf(e -> e.getValue() == null || e.getValue().equalsIgnoreCase("null") || e.getValue().isBlank());

        // PHASE: DATE PARSING
        LocalDate birth_date = parseDate(fields.get("dateOfBirth"));
        LocalDate issue_date = parseDate(fields.get("issueDate"));
        LocalDate expiry_date = parseDate(fields.get("expiryDate"));

        // PHASE: NAME BUILDING
        String holder_name = buildHolderName(fields);

        // PHASE: VALIDITY DETERMINATION
        boolean names_valid = fields.get("surname") != null && fields.get("givenNames") != null;
        boolean is_expired = expiry_date != null && expiry_date.isBefore(LocalDate.now());
        boolean has_doc_number = fields.get("documentNumber") != null;

        // Nomenclature Check
        boolean is_nomenclature_valid = validateNomenclature(
                issuing_country,
                doc_type,
                fields.get("documentNumber"));

        // Primary user rule: Document is valid if not expired and has basic info AND
        // nomenclature is valid
        boolean valid = !is_expired && names_valid && !doc_type.equals("UNKNOWN") && has_doc_number
                && is_nomenclature_valid;

        StringBuilder msg = new StringBuilder();
        if (valid) {
            msg.append("Valid document (Gemini Analysis)");
        } else {
            if (doc_type.equals("UNKNOWN")) {
                msg.append("Unknown type. ");
            }
            if (!names_valid) {
                msg.append("Missing names. ");
            }
            if (!has_doc_number) {
                msg.append("Missing number. ");
            } else if (!is_nomenclature_valid) {
                msg.append("Invalid number format for country (").append(issuing_country).append("). ");
            }
            if (is_expired) {
                msg.append("Expired document. ");
            }
            if (msg.length() == 0) {
                msg.append("Non-compliant document.");
            }
        }
        String validation_message = msg.toString().trim();

        // PHASE: CONFIDENCE CALCULATION (Simplified for Gemini)
        double confidence = 0.9; // We trust Gemini
        if (!names_valid || !has_doc_number) {
            confidence = 0.5;
        } else if (!is_nomenclature_valid) {
            confidence = 0.6; // Has number but failed format rules
        }

        log.info("=== Analysis Complete: type={}, country={}, confidence={}, valid={} ===", doc_type, issuing_country,
                confidence, valid);

        DocumentAnalysisResponse response = DocumentAnalysisResponse.builder()
                .document_type(doc_type)
                .issuing_country(issuing_country)
                .document_number(fields.get("documentNumber"))
                .holder_name(holder_name)
                .date_of_birth(birth_date)
                .issue_date(issue_date)
                .expiration_date(expiry_date)
                .is_valid(valid)
                .validation_message(validation_message)
                .confidence_score(confidence)
                .has_uncertainty(confidence < 0.6)
                .additional_fields(buildAdditionalFields(fields))
                .raw_extracted_text(raw_combined)
                .build();

        // Formal validation
        Set<ConstraintViolation<DocumentAnalysisResponse>> violations = validator_instance.validate(response);
        if (!violations.isEmpty()) {
            String formal_summary = violations.stream()
                    .map(v -> v.getMessage())
                    .distinct()
                    .collect(Collectors.joining(", "));
            response.setValidation_message(response.getValidationMessage() + " (Format: " + formal_summary + ")");
        }

        return response;
    }

    /**
     * Builds the holder name from surname and given names.
     *
     * @param fields the extracted fields map
     * @return the concatenated holder name
     */
    private String buildHolderName(Map<String, String> fields) {
        String surname = fields.get("surname");
        String given_names = fields.get("givenNames");
        if (surname != null && given_names != null) {
            return surname.trim() + " " + given_names.trim();
        }
        return surname != null ? surname.trim() : (given_names != null ? given_names.trim() : "UNKNOWN");
    }

    /**
     * Builds a map of additional fields not present in the main response.
     *
     * @param fields the extracted fields map
     * @return a map of additional fields
     */
    private Map<String, String> buildAdditionalFields(Map<String, String> fields) {
        Map<String, String> additional = new LinkedHashMap<>();
        // Filter out fields already present in the main response
        Set<String> top_level = Set.of("surname", "givenNames", "documentNumber", "dateOfBirth", "issueDate",
                "expiryDate", "expirationDate", "documentType", "issuingCountry");
        fields.forEach((key, value) -> {
            if (value != null && !value.isEmpty() && !top_level.contains(key)) {
                additional.put(key, value);
            }
        });
        return additional;
    }

    /**
     * Parses a date string into a LocalDate.
     *
     * @param date_str the date string
     * @return the parsed LocalDate or null if invalid
     */
    private LocalDate parseDate(String date_str) {
        if (date_str == null) {
            return null;
        }
        String clean = date_str.replaceAll("[^\\d./-]", "").trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (Exception ignored) {
                // Ignore exception to try next format
            }
        }
        return null;
    }

    /**
     * Validates the document number based on country and document type.
     *
     * @param country         the issuing country
     * @param doc_type        the document type
     * @param document_number the document number
     * @return true if valid according to nomenclature
     */
    private boolean validateNomenclature(String country, String doc_type, String document_number) {
        if (document_number == null || document_number.isBlank()) {
            return false;
        }
        if (country == null || country.equalsIgnoreCase("UNKNOWN")) {
            // Cannot strictly validate nomenclature without country, but ensure it exists
            return document_number.length() >= 5;
        }

        String normalized_country = country.toLowerCase().trim();
        String normalized_number = document_number.replaceAll("[^a-zA-Z0-9]", ""); // keep only alphanumerics

        // Specific Rules for CEMAC
        if (normalized_country.contains("gabon")) {
            // Gabon: NIP is typically 14 alphanumerics
            return normalized_number.length() == 14;
        } else if (normalized_country.contains("cameroun") || normalized_country.contains("cameroon")) {
            // Cameroon standard ranges
            if ("ID_CARD".equals(doc_type)) {
                return normalized_number.length() >= 9 && normalized_number.length() <= 17;
            } else if ("PASSPORT".equals(doc_type)) {
                return normalized_number.length() >= 7;
            }
            return normalized_number.length() >= 5;
        } else if (normalized_country.contains("tchad") || normalized_country.contains("chad")) {
            // Tchad: NNI validations (standard robust)
            return normalized_number.length() >= 5 && normalized_number.length() <= 20;
        } else if (normalized_country.contains("congo")) {
            // Congo (RDC or Brazzaville)
            return normalized_number.length() >= 5 && normalized_number.length() <= 25;
        } else if (normalized_country.contains("centrafrique") || normalized_country.contains("central african")) {
            // RCA validations
            return normalized_number.length() >= 5 && normalized_number.length() <= 20;
        }

        // Fallback for other valid string countries
        return document_number.length() >= 5 && document_number.length() <= 30;
    }
}