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

        // PASS 1: Anchor-Based Proximity Mapping (Ultra-Granular)
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].toUpperCase().trim();

            if (isAnchor(line, "NOM", "SURNAME"))
                f.put("surname", getClosestVal(lines, i, "NAME"));
            if (isAnchor(line, "PRÉNOMS", "GIVEN"))
                f.put("givenNames", getClosestVal(lines, i, "NAME"));
            if (isAnchor(line, "DATE DE NAISSANCE", "BIRTH"))
                f.put("dateOfBirth", getClosestVal(lines, i, "DATE"));
            if (isAnchor(line, "LIEU DE NAISSANCE", "PLACE OF BIRTH"))
                f.put("placeOfBirth", getClosestVal(lines, i, "TEXT"));
            if (isAnchor(line, "SEX")) {
                String val = getClosestVal(lines, i, "TEXT");
                if (val != null) {
                    if (val.contains("M"))
                        f.put("sex", "M");
                    else if (val.contains("F"))
                        f.put("sex", "F");
                }
            }
            if (isAnchor(line, "PROFESSION", "OCCUPATION"))
                f.put("occupation", getClosestVal(lines, i, "TEXT"));
            if (isAnchor(line, "TAILLE", "HEIGHT"))
                f.put("height", getClosestVal(lines, i, "HEIGHT"));
            if (isAnchor(line, "UNIQUE", "IDENTIFIER", "IDENTIFIANT", "NIC", "CNI")) {
                String id = getClosestVal(lines, i, "ID");
                if (id != null)
                    f.put("documentNumber", id);
            }
            if (isAnchor(line, "DÉLIVRANCE", "ISSUE"))
                f.put("issueDate", getClosestVal(lines, i, "DATE"));
            if (isAnchor(line, "EXPIRATION", "EXPIRY"))
                f.put("expiryDate", getClosestVal(lines, i, "DATE"));
        }

        // PASS 2: Pattern Discovery Fallback (Word by Word / Pattern by Pattern)
        recoverMissingFields(text, f);

        return f;
    }

    private boolean isAnchor(String line, String... keywords) {
        for (String kw : keywords) {
            if (line.contains(kw.toUpperCase()))
                return true;
        }
        // Character-level fuzzy matching for deformed labels (e.g., "S E X", "B1RTH",
        // "S3X")
        if (line.matches(".*S[ EKE7]{1,4}X.*") || line.matches(".*B[ 1I]{1,3}RTH.*"))
            return true;
        return false;
    }

    private String getClosestVal(String[] lines, int anchorIdx, String type) {
        // 1. Same line scan (stripped of labels)
        String sameLine = lines[anchorIdx].replaceAll(
                "(?i)(NOM|SURNAME|PRÉNOMS|GIVEN|NAMES|DATE|BIRTH|PLACE|SEX|PROFESSION|OCCUPATION|ISSUE|EXPIRY|UNIQUE|IDENTIFIER|CNI|NUMBER|IDENTIFIANT|TAILLE|HEIGHT)",
                "").trim();
        sameLine = sameLine.replaceAll("[:\\/\\-#*]", " ").trim();
        for (String part : sameLine.split("\\s+")) {
            if (isValid(part, type))
                return part;
        }

        // 2. Proximity scan (next 5 lines)
        for (int j = anchorIdx + 1; j < Math.min(lines.length, anchorIdx + 6); j++) {
            String candidate = lines[j].trim();
            if (candidate.isEmpty() || isLabel(candidate.toUpperCase()))
                continue;

            // Handle multi-word names directly
            if (type.equals("NAME") && isValid(candidate, type))
                return candidate;

            // Otherwise check individual tokens (word by word)
            for (String token : candidate.split("\\s+")) {
                String clean = token.replaceAll("[:\\/\\-#*]", "").trim();
                if (isValid(clean, type))
                    return clean;
            }
        }
        return null;
    }

    private boolean isValid(String val, String type) {
        if (val == null || val.length() < 1)
            return false;
        switch (type) {
            case "DATE":
                return val.matches(".*\\d{2}[./-]\\d{2}[./-]\\d{4}.*");
            case "ID":
                return val.matches("\\d{9,20}");
            case "HEIGHT":
                return val.matches(".*\\d[.,]\\d{2}.*");
            case "NAME":
                return val.matches("[A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]{2,}") && !isLabel(val.toUpperCase());
            case "TEXT":
                return val.length() > 2 && !isLabel(val.toUpperCase()) && !val.startsWith("<div");
            default:
                return true;
        }
    }

    private void recoverMissingFields(String text, Map<String, String> f) {
        // Deep Pattern Discovery (Global regex scan)
        if (f.get("dateOfBirth") == null || f.get("issueDate") == null || f.get("expiryDate") == null) {
            List<String> dates = findAll(text, "\\d{2}[./-]\\d{2}[./-]\\d{4}");
            if (dates.size() >= 2) {
                dates.sort((d1, d2) -> {
                    try {
                        return parseDate(d1).compareTo(parseDate(d2));
                    } catch (Exception e) {
                        return 0;
                    }
                });
                if (f.get("dateOfBirth") == null)
                    f.put("dateOfBirth", dates.get(0));
                if (f.get("expiryDate") == null)
                    f.put("expiryDate", dates.get(dates.size() - 1));
                if (f.get("issueDate") == null && dates.size() > 2)
                    f.put("issueDate", dates.get(1));
            }
        }

        if (f.get("documentNumber") == null) {
            String cni = find(text, "\\b\\d{17}\\b");
            if (cni != null)
                f.put("documentNumber", cni);
            else
                f.put("documentNumber", find(text, "\\b\\d{9,16}\\b"));
        }
    }

    private List<String> findAll(String text, String regex) {
        List<String> m = new ArrayList<>();
        Pattern p = Pattern.compile(regex);
        Matcher matcher = p.matcher(text);
        while (matcher.find())
            m.add(matcher.group());
        return m;
    }

    private boolean isLabel(String text) {
        String u = text.toUpperCase();
        return u.contains("SURNAME") || u.contains("NAMES") || u.contains("BIRTH") ||
                u.contains("PROFESSION") || u.contains("ISSUE") || u.contains("EXPIRY") ||
                u.contains("HEIGHT") || u.contains("SEX") || u.contains("UNIQUE") ||
                u.contains("IDENTIFIER") || u.contains("IDENTIFIANT") || u.contains("NIC") ||
                u.contains("NUMBER") || u.contains("CNI") || u.contains("DATE") ||
                u.contains("S.P.") || u.contains("FATHER") || u.contains("MOTHER") ||
                u.contains("REPUBLIC") || u.contains("CAMEROON") || u.contains("RÉPUBLIQUE");
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