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
                f.putAll(extractCNI(text));
                break;
            case "DRIVER_LICENSE":
                f.putAll(extractDriverLicense(text));
                break;
            case "PASSPORT":
                f.putAll(extractPassport(text));
                break;
        }

        // Apply generic extraction as fallback
        Map<String, String> generic = genericExtract(text);
        generic.forEach((k, v) -> f.putIfAbsent(k, v));

        return f;
    }

    private Map<String, String> genericExtract(String text) {
        Map<String, String> f = new HashMap<>();
        f.put("surname", findValue(text, "(?:NOM|SURNAME|LAST NAME)"));
        f.put("givenNames", findValue(text, "(?:PRÉNOMS|GIVEN NAMES|FIRST NAME)"));
        f.put("documentNumber", find(text, "(?:N°|NUMBER|ID|NI|CNI)[:\\s]*([A-Z0-9 ]{7,20})"));
        return f;
    }

    private Map<String, String> extractCNI(String text) {
        Map<String, String> f = new HashMap<>();

        f.put("documentNumber", findValue(text, "(?:NUMÉRO CNI|NIC NUMBER|ID NUMBER)"));
        f.put("surname", findValue(text, "(?:NOM / SURNAME|NOM\\s*/\\s*SURNAME)"));
        f.put("givenNames", findValue(text, "(?:PRÉNOMS / GIVEN NAMES|PRÉNOMS\\s*/\\s*GIVEN NAMES)"));
        f.put("dateOfBirth", findValue(text, "(?:DATE DE NAISSANCE / DATE OF BIRTH|DATE DE NAISSANCE)"));
        f.put("expiryDate", findValue(text, "(?:DATE D'EXPIRATION / DATE OF EXPIRY|DATE D'EXPIRATION)"));
        f.put("issueDate", findValue(text, "(?:DATE DE DÉLIVRANCE / DATE OF ISSUE|DATE DE DÉLIVRANCE)"));
        f.put("sex", findValue(text, "(?:SEXE / SEX|SEXE)"));
        f.put("placeOfBirth", findValue(text, "(?:LIEU DE NAISSANCE / PLACE OF BIRTH|LIEU DE NAISSANCE)"));
        f.put("occupation", findValue(text, "(?:PROFESSION / OCCUPATION|PROFESSION)"));
        f.put("height", findValue(text, "(?:TAILLE / HEIGHT|TAILLE)"));

        // Handle standalone ID at top
        if (f.get("documentNumber") == null) {
            f.put("documentNumber", find(text, "^\\s*([A-Z0-9]{8,15})\\n", Pattern.MULTILINE));
        }

        f.put("mrz", extractMRZ(text));
        return f;
    }

    private Map<String, String> extractDriverLicense(String text) {
        Map<String, String> f = new HashMap<>();

        // Use patterns based on numbered fields
        f.put("surname", find(text, "^1\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)$", Pattern.MULTILINE));
        f.put("givenNames", find(text, "^2\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)$", Pattern.MULTILINE));

        // 3. Date and place of birth
        Pattern p3 = Pattern.compile("^3\\.\\s*([\\d.-]+),\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)$",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            f.put("dateOfBirth", m3.group(1).trim());
            f.put("placeOfBirth", m3.group(2).trim());
        }

        f.put("issueDate", find(text, "4a\\.\\s+([\\d./-]+)"));
        f.put("expiryDate", find(text, "4b\\.\\s+([\\d./-]+)"));
        f.put("authority", find(text, "4c\\.\\s+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s\\.]+)"));
        f.put("documentNumber", find(text, "5\\.\\s+([A-Z0-9-]+)"));
        f.put("categories", find(text, "9\\.\\s+([A-E\\d, ]+)(?=\\s|\\n)"));

        // Standalone number as fallback
        if (f.get("documentNumber") == null) {
            f.put("documentNumber", find(text, "^\\s*([A-Z]{2}-\\d{6}-\\d{2})\\s*$", Pattern.MULTILINE));
        }

        return f;
    }

    private Map<String, String> extractPassport(String text) {
        Map<String, String> f = new HashMap<>();

        f.put("documentNumber", findValue(text, "(?:No de passeport / Passport no\\.)"));
        f.put("surname", findValue(text, "1\\.\\s*Nom\\s*/\\s*Surname"));
        f.put("givenNames", findValue(text, "2\\.\\s*Prénoms\\s*/\\s*Given names"));
        f.put("nationality", findValue(text, "3\\.\\s*Nationalité\\s*/\\s*Nationality"));
        f.put("dateOfBirth", findValue(text, "4\\.\\s*Date de naissance\\s*/\\s*Date of birth"));
        f.put("sex", findValue(text, "5\\.\\s*Sexe\\s*/\\s*Sex"));
        f.put("placeOfBirth", findValue(text, "6\\.\\s*Lieu de naissance\\s*/\\s*Place of birth"));
        f.put("issueDate", findValue(text, "7\\.\\s*Date de délivrance\\s*/\\s*Date of Issue"));
        f.put("expiryDate", findValue(text, "8\\.\\s*Date d'expiration\\s*/\\s*Date of expiry"));
        f.put("occupation", findValue(text, "9\\.\\s*Profession\\s*/\\s*Occupation"));

        // Combined Height and CAN
        Pattern p10 = Pattern.compile(
                "(?:10\\.\\s*Taille\\s*/\\s*Height\\s+11\\.\\s*CAN)\\s*\\n+([\\d.,]+\\s*m)\\s+(\\d+)",
                Pattern.CASE_INSENSITIVE);
        Matcher m10 = p10.matcher(text);
        if (m10.find()) {
            f.put("height", m10.group(1).trim());
            f.put("canNumber", m10.group(2).trim());
        }

        f.put("placeOfIssue", findValue(text, "12\\.\\s*Lieu de délivrance\\s*/\\s*Place of issue"));
        f.put("mrz", extractMRZ(text));

        // Standalone number fallback
        if (f.get("documentNumber") == null) {
            f.put("documentNumber", find(text, "^\\s*([A-Z]{2}\\d{7})\\s*$", Pattern.MULTILINE));
        }

        return f;
    }

    /**
     * Finds a value after a label, handling both same-line and next-line scenarios.
     */
    private String findValue(String text, String labelPattern) {
        // Try same line: LABEL VALUE
        String sameLine = find(text, labelPattern + "[:\\s/]+([^\\n]{2,})");
        if (sameLine != null && !isLabel(sameLine.toUpperCase()))
            return sameLine;

        // Try next line: LABEL \n VALUE
        String nextLine = find(text, labelPattern + "[:\\s/]*\\n+([^\\n]{2,})");
        if (nextLine != null && !isLabel(nextLine.toUpperCase()))
            return nextLine;

        return null;
    }

    private String find(String text, String pattern) {
        return find(text, pattern, Pattern.CASE_INSENSITIVE);
    }

    private String find(String text, String pattern, int flags) {
        Pattern p = Pattern.compile(pattern, flags);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String val = m.group(1).trim();
            // Clean up Markdown labels or other noise if present
            val = val.replaceAll("^\\s*[#*\\-]+\\s*", "").trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private String extractMRZ(String text) {
        // Handle Passport MRZ (2 lines of 44 chars)
        Pattern pMRZ = Pattern.compile("([A-Z0-9<]{44}\\n[A-Z0-9<]{44})");
        Matcher mMRZ = pMRZ.matcher(text.replace(" ", ""));
        if (mMRZ.find())
            return mMRZ.group(1);

        // Handle CNI MRZ (variant lines)
        Pattern cniMRZ = Pattern.compile("((?:ICMR|ID|I<)[A-Z0-9<]{20,}\\n[A-Z0-9<]{20,})");
        Matcher mCNI = cniMRZ.matcher(text.replace(" ", ""));
        if (mCNI.find())
            return mCNI.group(1);

        return null;
    }

    private boolean isLabel(String text) {
        return text.contains("SURNAME") || text.contains("NAMES") || text.contains("BIRTH") ||
                text.contains("PROFESSION") || text.contains("ISSUE") || text.contains("EXPIRY") ||
                text.contains("HEIGHT") || text.contains("SEX") || text.contains("UNIQUE") ||
                text.contains("IDENTIFIER") || text.contains("IDENTIFIANT") || text.contains("NIC") ||
                text.contains("NUMBER") || text.contains("CNI") || text.contains("DATE") ||
                text.contains("S.P.") || text.contains("FATHER") || text.contains("MOTHER") ||
                text.contains("LIEU") || text.contains("PLACE") || text.contains("NATIONALITY") ||
                text.contains("OCCUPATION") || text.contains("TAILLE") || text.contains("DÉLIVRANCE") ||
                text.contains("NAISSANCE") || text.contains("EXPIRATION");
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