package Projects.Network.service;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.model.DocumentEntity;
import Projects.Network.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DocumentAnalysisService - Version Universelle et Adaptative
 *
 * Principe: Si rawExtractedText contient des données, on EXTRAIT TOUT
 * Support de TOUS les formats de documents camerounais
 *
 * @author Thomas Djotio Ndié
 * @version 4.0 - Universal Parser
 */
@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

    private final DocumentRepository documentRepository;
    private final EnhancedDocumentService enhancedDocumentService;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy")
    };

    public Mono<DocumentAnalysisResponse> analyzeDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .flatMap(document -> enhancedDocumentService.extractMarkdownText(document.getMinioPath())
                        .map(extractedText -> performUniversalAnalysis(document, extractedText))
                );
    }

    private DocumentAnalysisResponse performUniversalAnalysis(DocumentEntity document, String extractedText) {
        System.out.println("\n🔍 === UNIVERSAL DOCUMENT ANALYSIS ===");
        System.out.println("Raw text length: " + extractedText.length() + " chars");

        if (extractedText == null || extractedText.trim().isEmpty()) {
            System.out.println("❌ Empty text - cannot extract");
            return buildEmptyResponse(extractedText);
        }

        // Clean text
        String cleanedText = cleanText(extractedText);

        // Extract with UNIVERSAL patterns (works for ANY format)
        Map<String, String> fields = extractUniversal(cleanedText);

        // Parse dates
        LocalDate dateOfBirth = parseDate(fields.get("dateOfBirth"));
        LocalDate issueDate = parseDate(fields.get("issueDate"));
        LocalDate expiryDate = parseDate(fields.get("expiryDate"));

        // Build name
        String holderName = buildFullName(fields.get("surname"), fields.get("givenNames"));

        // Validation
        boolean isValid = validateExpiration(expiryDate);
        double confidence = calculateConfidence(fields);

        System.out.println("✅ Extracted " + fields.size() + " fields (confidence: " + String.format("%.0f%%", confidence * 100) + ")");
        logExtractedFields(fields);

        return DocumentAnalysisResponse.builder()
                .documentType(detectDocumentType(extractedText))
                .documentNumber(fields.get("documentNumber"))
                .holderName(holderName)
                .dateOfBirth(dateOfBirth)
                .issueDate(issueDate)
                .expirationDate(expiryDate)
                .isValid(isValid)
                .validationMessage(isValid ? "Document valide" : (expiryDate == null ? "Validité inconnue" : "Document expiré"))
                .confidenceScore(confidence)
                .hasUncertainty(confidence < 0.5)
                .additionalFields(buildAdditionalFields(fields))
                .rawExtractedText(extractedText)
                .build();
    }

    /**
     * Clean text: remove HTML, normalize
     */
    private String cleanText(String text) {
        return text
                .replaceAll("<[^>]+>", "\n")
                .replaceAll("\\s*\\n\\s*", "\n")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * UNIVERSAL EXTRACTION - Works for ANY document format
     * Strategy: Search for labels in BOTH French and English, extract value after
     */
    private Map<String, String> extractUniversal(String text) {
        Map<String, String> fields = new HashMap<>();

        // 1. SURNAME / NOM
        fields.put("surname",
                findValueAfterLabel(text,
                        "NOM", "SURNAME", "Nom", "Surname", "1\\. Nom"));

        // 2. GIVEN NAMES / PRÉNOMS
        fields.put("givenNames",
                findValueAfterLabel(text,
                        "PRÉNOMS", "GIVEN NAMES", "Prénoms", "Given names", "2\\. Prénoms", "PRENOMS"));

        // 3. DATE OF BIRTH / DATE DE NAISSANCE
        fields.put("dateOfBirth",
                findDateAfterLabel(text,
                        "DATE DE NAISSANCE", "DATE OF BIRTH", "Date de naissance", "4\\. Date de naissance", "NE LE", "BORN"));

        // 4. PLACE OF BIRTH / LIEU DE NAISSANCE
        fields.put("placeOfBirth",
                findValueAfterLabel(text,
                        "LIEU DE NAISSANCE", "PLACE OF BIRTH", "Lieu de naissance", "6\\. Lieu de naissance", "LIFU DE NAISSANCE"));

        // 5. SEX / SEXE
        fields.put("sex",
                findValueAfterLabel(text,
                        "SEXE", "SEX", "Sexe", "Sex", "5\\. Sexe"));

        // 6. HEIGHT / TAILLE
        fields.put("height",
                findValueAfterLabel(text,
                        "TAILLE", "HEIGHT", "Taille", "Height", "10\\. Taille"));

        // 7. OCCUPATION / PROFESSION
        fields.put("occupation",
                findValueAfterLabel(text,
                        "PROFESSION", "OCCUPATION", "Profession", "Occupation", "9\\. Profession"));

        // 8. NATIONALITY / NATIONALITÉ
        fields.put("nationality",
                findValueAfterLabel(text,
                        "NATIONALITÉ", "NATIONALITY", "Nationalité", "Nationality", "3\\. Nationalité", "NATIONALITE"));

        // 9. ISSUE DATE / DATE DE DÉLIVRANCE
        fields.put("issueDate",
                findDateAfterLabel(text,
                        "DATE DE DÉLIVRANCE", "DATE OF ISSUE", "Date de délivrance", "7\\. Date de délivrance", "DELIVRE LE"));

        // 10. EXPIRY DATE / DATE D'EXPIRATION
        fields.put("expiryDate",
                findDateAfterLabel(text,
                        "DATE D'EXPIRATION", "DATE OF EXPIRY", "Date d'expiration", "8\\. Date d'expiration", "VALABLE JUSQU"));

        // 11. PLACE OF ISSUE / LIEU DE DÉLIVRANCE
        fields.put("placeOfIssue",
                findValueAfterLabel(text,
                        "LIEU DE DÉLIVRANCE", "PLACE OF ISSUE", "Lieu de délivrance", "12\\. Lieu de délivrance"));

        // 12. DOCUMENT NUMBER - Universal search
        fields.put("documentNumber", extractDocumentNumber(text));

        // 13. COUNTRY CODE
        fields.put("countryCode", extractCountryCode(text));

        // 14. CAN NUMBER
        fields.put("canNumber", extractCANNumber(text));

        // 15. DOCUMENT TYPE
        fields.put("documentType", extractDocType(text));

        // 16. MRZ (if exists)
        fields.put("mrz", extractMRZ(text));

        return fields;
    }

    /**
     * Find value after a label (supports multiple label variants)
     */
    private String findValueAfterLabel(String text, String... labels) {
        for (String label : labels) {
            // Pattern: label followed by optional separators, then capture value
            Pattern pattern = Pattern.compile(
                    label + "\\s*[:/]?\\s*\\n?\\s*([A-ZÀ-Ÿ0-9][A-ZÀ-Ÿa-zàâäçèéêëïîôùûü0-9\\s,.-]+?)(?=\\n[A-ZÀ-Ÿ]{2,}|\\n\\n|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );

            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                // Clean up value
                value = value.replaceAll("\\s+", " ");

                if (!value.isEmpty() && value.length() > 1) {
                    System.out.println("✓ Found after '" + label + "': " + value);
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Find date after a label
     */
    private String findDateAfterLabel(String text, String... labels) {
        for (String label : labels) {
            Pattern pattern = Pattern.compile(
                    label + "\\s*[:/]?\\s*\\n?\\s*([\\d]{1,2}[\\./-][\\d]{1,2}[\\./-][\\d]{2,4})",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );

            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                System.out.println("✓ Found date after '" + label + "': " + value);
                return value;
            }
        }

        return null;
    }

    /**
     * Extract document number (universal patterns)
     */
    private String extractDocumentNumber(String text) {
        // Pattern 1: Passport format (AB301964)
        Pattern p1 = Pattern.compile("\\b([A-Z]{2}\\d{6,7})\\b");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            System.out.println("✓ Document number: " + m1.group(1));
            return m1.group(1);
        }

        // Pattern 2: ID Card format (10-15 alphanumeric)
        Pattern p2 = Pattern.compile("(?:N°|No|NUM|NUMBER)\\s*[:/]?\\s*([A-Z0-9]{8,15})");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            System.out.println("✓ Document number: " + m2.group(1));
            return m2.group(1);
        }

        return null;
    }

    /**
     * Extract country code
     */
    private String extractCountryCode(String text) {
        Pattern pattern = Pattern.compile("\\b(CMR|CAMEROUN|CAMEROON)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "CMR";
        }
        return null;
    }

    /**
     * Extract CAN number
     */
    private String extractCANNumber(String text) {
        Pattern pattern = Pattern.compile("CAN\\s*[:/]?\\s*(\\d{6,8})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract document type
     */
    private String extractDocType(String text) {
        Pattern pattern = Pattern.compile("Type\\s*[:/]?\\s*([A-Z]{1,3})\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract MRZ
     */
    private String extractMRZ(String text) {
        Pattern pattern = Pattern.compile("PP[A-Z]{3}[A-Z<]+<<[A-Z<]+[A-Z0-9<]+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * Detect document type
     */
    private String detectDocumentType(String text) {
        String upper = text.toUpperCase();

        if (upper.contains("PASSEPORT") || upper.contains("PASSPORT")) {
            return "PASSPORT";
        } else if (upper.contains("CARTE NATIONALE") || upper.contains("CNI") || upper.contains("IDENTITY CARD")) {
            return "ID_CARD";
        } else if (upper.contains("PERMIS") || upper.contains("DRIVER") || upper.contains("LICENSE")) {
            return "DRIVER_LICENSE";
        }

        return "UNKNOWN";
    }

    /**
     * Build full name
     */
    private String buildFullName(String surname, String givenNames) {
        if (surname != null && givenNames != null) {
            return surname + " " + givenNames;
        }
        return surname != null ? surname : givenNames;
    }

    /**
     * Build additional fields
     */
    private Map<String, String> buildAdditionalFields(Map<String, String> fields) {
        Map<String, String> additional = new HashMap<>();

        addIfPresent(additional, "nationality", fields.get("nationality"));
        addIfPresent(additional, "sex", fields.get("sex"));
        addIfPresent(additional, "placeOfBirth", fields.get("placeOfBirth"));
        addIfPresent(additional, "occupation", fields.get("occupation"));
        addIfPresent(additional, "height", fields.get("height"));
        addIfPresent(additional, "canNumber", fields.get("canNumber"));
        addIfPresent(additional, "placeOfIssue", fields.get("placeOfIssue"));
        addIfPresent(additional, "documentType", fields.get("documentType"));
        addIfPresent(additional, "countryCode", fields.get("countryCode"));
        addIfPresent(additional, "mrz", fields.get("mrz"));

        return additional;
    }

    private void addIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    /**
     * Parse date
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    /**
     * Validate expiration
     */
    private boolean validateExpiration(LocalDate expiryDate) {
        if (expiryDate == null) return false;
        return !expiryDate.isBefore(LocalDate.now());
    }

    /**
     * Calculate confidence
     */
    private double calculateConfidence(Map<String, String> fields) {
        String[] coreFields = {"surname", "givenNames", "dateOfBirth"};
        int coreCount = 0;

        for (String field : coreFields) {
            if (fields.get(field) != null && !fields.get(field).isEmpty()) {
                coreCount++;
            }
        }

        // Minimum 33% if any field extracted
        double baseScore = Math.max(0.33, coreCount / 3.0);

        // Bonus for additional fields
        long totalFields = fields.values().stream().filter(v -> v != null && !v.isEmpty()).count();
        double bonus = Math.min(0.4, totalFields * 0.05);

        return Math.min(1.0, baseScore + bonus);
    }

    /**
     * Log extracted fields
     */
    private void logExtractedFields(Map<String, String> fields) {
        System.out.println("\n📋 Extracted fields:");
        fields.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                System.out.println("  • " + key + ": " + value);
            }
        });
        System.out.println();
    }

    /**
     * Build empty response
     */
    private DocumentAnalysisResponse buildEmptyResponse(String rawText) {
        return DocumentAnalysisResponse.builder()
                .documentType("UNKNOWN")
                .documentNumber(null)
                .holderName(null)
                .dateOfBirth(null)
                .issueDate(null)
                .expirationDate(null)
                .isValid(false)
                .validationMessage("Données insuffisantes")
                .confidenceScore(0.0)
                .hasUncertainty(true)
                .additionalFields(new HashMap<>())
                .rawExtractedText(rawText)
                .build();
    }
}