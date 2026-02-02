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

@Service
@RequiredArgsConstructor
public class DocumentAnalysisService {

    private final DocumentRepository documentRepository;
    private final EnhancedDocumentService enhancedDocumentService;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    /**
     * Analyze document with separate front and back files
     */
    public Mono<DocumentAnalysisResponse> analyzeDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found")))
                .flatMap(doc -> {
                    Mono<String> frontMono = enhancedDocumentService.extractMarkdownText(doc.getMinioPath());
                    Mono<String> backMono = doc.getBackMinioPath() != null
                            ? enhancedDocumentService.extractMarkdownText(doc.getBackMinioPath())
                            : Mono.just("");

                    return Mono.zip(frontMono, backMono)
                            .map(tuple -> analyze(tuple.getT1(), tuple.getT2()));
                });
    }

    private DocumentAnalysisResponse analyze(String front, String back) {
        String combined = front + "\n" + back;
        String clean = combined.replaceAll("<[^>]+>", "\n").trim();

        Map<String, String> fields = extractFields(clean);
        String docType = detectType(clean);

        LocalDate birthDate = parseDate(fields.get("dateOfBirth"));
        LocalDate issueDate = parseDate(fields.get("issueDate"));
        LocalDate expiryDate = parseDate(fields.get("expiryDate"));

        String name = buildName(fields.get("surname"), fields.get("givenNames"));
        boolean valid = expiryDate != null && !expiryDate.isBefore(LocalDate.now());
        double confidence = calcConfidence(fields);

        return DocumentAnalysisResponse.builder()
                .documentType(docType)
                .documentNumber(fields.get("documentNumber"))
                .holderName(name)
                .dateOfBirth(birthDate)
                .issueDate(issueDate)
                .expirationDate(expiryDate)
                .isValid(valid)
                .validationMessage(valid ? "Document valide" : "Document expiré")
                .confidenceScore(confidence)
                .hasUncertainty(confidence < 0.6)
                .additionalFields(buildAdditional(fields))
                .rawExtractedText(combined)
                .build();
    }

    private Map<String, String> extractFields(String text) {
        Map<String, String> f = new HashMap<>();
        String docType = detectType(text);

        switch (docType) {
            case "ID_CARD":
                f = extractCNI(text);
                break;
            case "DRIVER_LICENSE":
                f = extractDriverLicense(text);
                break;
            case "PASSPORT":
                f = extractPassport(text);
                break;
            default:
                break;
        }

        // Apply generic extraction as fallback for missing critical fields
        Map<String, String> generic = genericExtract(text);
        for (String key : generic.keySet()) {
            if (f.get(key) == null || f.get(key).isEmpty()) {
                f.put(key, generic.get(key));
            }
        }

        return f;
    }

    private Map<String, String> genericExtract(String text) {
        Map<String, String> f = new HashMap<>();

        // Generic Name detection
        if (f.get("surname") == null)
            f.put("surname", find(text, "(?:NOM|SURNAME|LAST NAME)[:\\s]*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
        if (f.get("givenNames") == null)
            f.put("givenNames", find(text, "(?:PRÉNOMS|GIVEN NAMES|FIRST NAME)[:\\s]*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));

        // Generic Identifier detection
        if (f.get("documentNumber") == null)
            f.put("documentNumber", find(text, "(?:N°|NUMBER|IDENTIFIER|ID|NI)[:\\s]*([A-Z0-9 ]{7,20})"));

        return f;
    }

    private Map<String, String> extractCNI(String text) {
        Map<String, String> f = new HashMap<>();
        String[] lines = text.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toUpperCase().trim();

            // 1. Document Number (NIC / UNIQUE IDENTIFIER)
            if (line.contains("UNIQUE IDENTIFIER") || line.contains("IDENTIFIANT UNIQUE") || line.contains("NIC NUMBER")
                    || line.contains("NUMÉRO CNI")) {
                f.put("documentNumber", getNextVal(lines, i, true));
            }

            // 2. Surname
            if (line.contains("NOM") && line.contains("SURNAME")) {
                f.put("surname", getNextVal(lines, i, false));
            }

            // 3. Given Names
            if (line.contains("PRÉNOMS") || (line.contains("GIVEN") && line.contains("NAMES"))) {
                f.put("givenNames", getNextVal(lines, i, false));
            }

            // 4. Date of Birth
            if (line.contains("DATE DE NAISSANCE") || line.contains("DATE OF BIRTH")) {
                f.put("dateOfBirth", getNextVal(lines, i, false));
            }

            // 5. Place of Birth
            if (line.contains("LIEU DE NAISSANCE") || line.contains("PLACE OF BIRTH")) {
                f.put("placeOfBirth", getNextVal(lines, i, false));
            }

            // 6. Sex (Fuzzy match for "SEKE7SEX", "SEXE/SEX")
            if (line.contains("SEX") || line.contains("SEXE")) {
                String val = getNextVal(lines, i, false);
                if (val != null && (val.startsWith("M") || val.startsWith("F"))) {
                    f.put("sex", val.substring(0, 1));
                }
            }

            // 7. Height
            if (line.contains("TAILLE") || line.contains("HEIGHT")) {
                String val = getNextVal(lines, i, false);
                if (val != null && val.matches(".*\\d[.,]\\d{2}.*")) {
                    f.put("height", val);
                }
            }

            // 8. Occupation
            if (line.contains("PROFESSION") || line.contains("OCCUPATION")) {
                f.put("occupation", getNextVal(lines, i, false));
            }

            // 9. Issue Date
            if (line.contains("DATE DE DÉLIVRANCE") || (line.contains("DATE") && line.contains("ISSUE"))) {
                f.put("issueDate", getNextVal(lines, i, false));
            }

            // 10. Expiry Date
            if (line.contains("EXPIRATION") || (line.contains("DATE") && line.contains("EXPIRY"))) {
                f.put("expiryDate", getNextVal(lines, i, false));
            }
        }

        return f;
    }

    /**
     * Helper to get the next meaningful value after an anchor line.
     */
    private String getNextVal(String[] lines, int currentIndex, boolean isId) {
        String currentLine = lines[currentIndex].toUpperCase();

        // Try to see if the value is on the SAME line after the label
        // This handles cases like "NOM/SURNAME ETSIKE"
        String[] parts = lines[currentIndex].split("\\s{2,}|[:\\/]");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty() || currentLine.contains(p.toUpperCase()))
                continue;
            if (isId && p.matches(".*\\d{5,}.*"))
                return p;
            if (!isId && p.length() > 2)
                return p;
        }

        for (int j = currentIndex + 1; j < Math.min(lines.length, currentIndex + 5); j++) {
            String candidate = lines[j].trim();
            if (candidate.isEmpty())
                continue;

            String upper = candidate.toUpperCase();

            // Skip labels and structural noise
            if (isLabel(upper))
                continue;
            if (candidate.startsWith("<div"))
                continue;

            // Strip structural noise
            candidate = candidate.replaceAll("^\\s*[#*\\-]+\\s*", "").trim();

            // For IDs, we expect digits
            if (isId && !candidate.matches(".*\\d{5,}.*"))
                continue;

            return candidate;
        }
        return null;
    }

    private boolean isLabel(String text) {
        return text.contains("SURNAME") || text.contains("NAMES") || text.contains("BIRTH") ||
                text.contains("PROFESSION") || text.contains("ISSUE") || text.contains("EXPIRY") ||
                text.contains("HEIGHT") || text.contains("SEX") || text.contains("UNIQUE") ||
                text.contains("IDENTIFIER") || text.contains("IDENTIFIANT") || text.contains("NIC") ||
                text.contains("NUMBER") || text.contains("CNI") || text.contains("DATE") ||
                text.contains("S.P.") || text.contains("FATHER") || text.contains("MOTHER");
    }

    private Map<String, String> extractDriverLicense(String text) {
        Map<String, String> f = new HashMap<>();
        f.put("surname", find(text, "1\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|2\\.)"));
        f.put("givenNames", find(text, "2\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|3\\.)"));

        Pattern p = Pattern.compile("3\\.\\s*([\\d.-]+),\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            f.put("dateOfBirth", m.group(1));
            f.put("placeOfBirth", m.group(2));
        }

        f.put("issueDate", find(text, "4a\\.\\s+([\\d.-]+)"));
        f.put("expiryDate", find(text, "4b\\.\\s+([\\d.-]+)"));
        f.put("authority", find(text, "4c\\.\\s+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s\\.]+)"));
        f.put("reference", find(text, "4d\\.\\s+([A-Z0-9-]+)"));
        f.put("documentNumber", find(text, "5\\.\\s+([A-Z0-9-]+)"));
        f.put("categories", find(text, "9\\.\\s+([A-E0-9]+)"));

        return f;
    }

    private Map<String, String> extractPassport(String text) {
        Map<String, String> f = new HashMap<>();
        f.put("documentNumber", find(text, "(?:No de passeport|Passport no)\\.?\\s*\\n+([A-Z0-9]+)"));
        f.put("surname", find(text, "1\\.\\s*Nom\\s*/\\s*Surname\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|2\\.)"));
        f.put("givenNames",
                find(text, "2\\.\\s*Prénoms\\s*/\\s*Given names\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|3\\.)"));
        f.put("nationality",
                find(text, "3\\.\\s*Nationalité\\s*/\\s*Nationality\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ/\\s-]+?)(?=\\n|4\\.)"));
        f.put("dateOfBirth", find(text, "4\\.\\s*Date de naissance\\s*/\\s*Date of birth\\s*\\n+([\\d./-]+)"));
        f.put("sex", find(text, "5\\.\\s*Sexe\\s*/\\s*Sex\\s*\\n+([MF])"));
        f.put("placeOfBirth", find(text,
                "6\\.\\s*Lieu de naissance\\s*/\\s*Place of birth\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|7\\.)"));
        f.put("issueDate", find(text, "7\\.\\s*Date de délivrance\\s*/\\s*Date of issue\\s*\\n+([\\d./-]+)"));
        f.put("expiryDate", find(text, "8\\.\\s*Date d'expiration\\s*/\\s*Date of expiry\\s*\\n+([\\d./-]+)"));
        f.put("occupation",
                find(text, "9\\.\\s*Profession\\s*/\\s*Occupation\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|10\\.)"));

        Pattern p = Pattern.compile("10\\.\\s*Taille\\s*/\\s*Height\\s+11\\.\\s*CAN\\s*\\n+([\\d.,]+\\s*m)\\s+(\\d+)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            f.put("height", m.group(1));
            f.put("canNumber", m.group(2));
        }

        f.put("placeOfIssue", find(text,
                "12\\.\\s*Lieu de délivrance\\s*/\\s*Place of issue\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|13\\.)"));
        f.put("mrz", find(text, "(P[A-Z0-9<]{43}\\n[A-Z0-9<]{44})"));

        return f;
    }

    private String find(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String value = m.group(1).trim();
            // Clean up Markdown and extra spaces
            return value.replaceAll("^\\s*[#*\\-]+\\s*", "").trim();
        }
        return null;
    }

    private String detectType(String text) {
        String u = text.toUpperCase();
        if (u.contains("PASSEPORT") || u.contains("PASSPORT"))
            return "PASSPORT";
        if (u.contains("PERMIS") || u.contains("DRIVING LICENCE") || u.contains("DRIVING LICENSE"))
            return "DRIVER_LICENSE";
        if (u.contains("CNI") || u.contains("CARTE NATIONALE") || u.contains("NATIONAL IDENTITY CARD")
                || u.contains("NOM DU PÈRE") || u.contains("REPUBLIC OF CAMEROON")
                || u.contains("RÉPUBLIQUE DU CAMEROUN"))
            return "ID_CARD";
        return "UNKNOWN";
    }

    private String buildName(String surname, String given) {
        if (surname != null && given != null)
            return surname.trim() + " " + given.trim();
        return surname != null ? surname.trim() : (given != null ? given.trim() : "");
    }

    private Map<String, String> buildAdditional(Map<String, String> fields) {
        Map<String, String> add = new HashMap<>();
        String[] keys = { "nationality", "sex", "placeOfBirth", "occupation", "height",
                "canNumber", "placeOfIssue", "documentType", "countryCode", "mrz",
                "categories", "reference", "authority" };
        for (String k : keys) {
            if (fields.get(k) != null)
                add.put(k, fields.get(k));
        }
        return add;
    }

    private LocalDate parseDate(String date) {
        if (date == null)
            return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(date, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private double calcConfidence(Map<String, String> fields) {
        long count = fields.values().stream().filter(v -> v != null && !v.isEmpty()).count();
        return Math.min(1.0, count / 13.0);
    }
}