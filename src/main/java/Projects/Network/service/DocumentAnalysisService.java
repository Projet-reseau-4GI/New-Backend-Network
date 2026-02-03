package Projects.Network.service;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    // Date patterns commonly found in Cameroon documents
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d.MM.yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy")
    };

    // Regex patterns for Cameroon document numbers
    private static final Pattern CNI_NUMBER_PATTERN = Pattern.compile("\\b(\\d{17,20})\\b");
    private static final Pattern CNI_AA_PATTERN = Pattern.compile("\\b(AA\\s*\\d{8,10})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSPORT_NUMBER = Pattern.compile("\\b([A-Z]{2}\\d{6,8})\\b");
    private static final Pattern LICENSE_NUMBER = Pattern.compile("\\b(CE-?\\d{5,8}-?\\d{2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}[./-]\\d{2}[./-]\\d{4})\\b");
    private static final Pattern NAME_PATTERN = Pattern
            .compile("\\b([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜŒ][A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜŒ\\s-]{2,25})\\b");

    public Mono<DocumentAnalysisResponse> analyzeDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found")))
                .flatMap(doc -> {
                    Mono<String> frontMono = enhancedDocumentService.extractMarkdownText(doc.getMinioPath());
                    Mono<String> backMono = doc.getBackMinioPath() != null
                            ? enhancedDocumentService.extractMarkdownText(doc.getBackMinioPath())
                            : Mono.just("");
                    return Mono.zip(frontMono, backMono).map(tuple -> analyze(tuple.getT1(), tuple.getT2()));
                });
    }

    private DocumentAnalysisResponse analyze(String front, String back) {
        String rawCombined = front + "\n" + back;
        log.info("=== Starting Ultra-Power Document Analysis ===");
        log.info("Raw text length: {}", rawCombined.length());

        // PHASE 1: PRE-PROCESSING
        String cleanText = preProcess(rawCombined);
        List<String> tokens = tokenize(cleanText);
        List<String> lines = Arrays.stream(cleanText.split("\\n+"))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        // PHASE 2: DOCUMENT TYPE DETECTION (Multi-Strategy)
        String docType = detectDocumentType(rawCombined, cleanText);
        log.info("Detected document type: {}", docType);

        // PHASE 3: SECURITY FEATURE VALIDATION
        Map<String, Object> security = validateSecurityFeatures(rawCombined);
        log.info("Security features: emblems={}, signatures={}", security.get("hasEmblems"),
                security.get("signatureCount"));

        // PHASE 4: MULTI-STRATEGY EXTRACTION
        Map<String, String> fields = new LinkedHashMap<>();

        // Strategy A: Label-based extraction (label on line N, value on line N+1 or
        // same line)
        extractByLabels(lines, fields);

        // Strategy B: Type-specific numbered extraction (for passports/licenses)
        extractByNumberedFields(cleanText, docType, fields);

        // Strategy C: Pattern discovery (find all dates, numbers, names and assign
        // contextually)
        extractByPatternDiscovery(rawCombined, cleanText, tokens, docType, fields);

        // Strategy D: MRZ parsing (for passports and modern CNIs)
        extractFromMRZ(rawCombined, fields);

        // PHASE 5: NORMALIZATION AND CLEANING
        normalizeFields(fields);

        // PHASE 6: DATE PARSING
        LocalDate birthDate = parseDate(fields.get("dateOfBirth"));
        LocalDate issueDate = parseDate(fields.get("issueDate"));
        LocalDate expiryDate = parseDate(fields.get("expiryDate"));

        // PHASE 7: NAME BUILDING
        String holderName = buildHolderName(fields);

        // PHASE 8: VALIDITY DETERMINATION
        boolean isExpired = expiryDate != null && expiryDate.isBefore(LocalDate.now());
        boolean hasEmblems = (boolean) security.getOrDefault("hasEmblems", false);
        int sigCount = (int) security.getOrDefault("signatureCount", 0);
        boolean hasDocNumber = fields.get("documentNumber") != null && fields.get("documentNumber").length() >= 6;

        boolean valid = !isExpired && (hasEmblems || hasDocNumber) && !docType.equals("UNKNOWN");
        String validationMessage = buildValidationMessage(valid, isExpired, hasEmblems, docType);

        // PHASE 9: CONFIDENCE CALCULATION
        double confidence = calculateAdvancedConfidence(fields, security, docType, birthDate, expiryDate);

        log.info("=== Analysis Complete: type={}, confidence={}, valid={} ===", docType, confidence, valid);

        return DocumentAnalysisResponse.builder()
                .documentType(docType)
                .documentNumber(fields.get("documentNumber"))
                .holderName(holderName)
                .dateOfBirth(birthDate)
                .issueDate(issueDate)
                .expirationDate(expiryDate)
                .isValid(valid)
                .validationMessage(validationMessage)
                .confidenceScore(confidence)
                .hasUncertainty(confidence < 0.6)
                .additionalFields(buildAdditionalFields(fields, security))
                .rawExtractedText(rawCombined)
                .build();
    }

    // ===================== PRE-PROCESSING =====================

    private String preProcess(String raw) {
        // Remove HTML/XML tags but preserve structure
        String clean = raw.replaceAll("<[^>]+>", "\n");
        // Normalize whitespace
        clean = clean.replaceAll("[ \\t]+", " ");
        // Normalize line breaks
        clean = clean.replaceAll("\\n{3,}", "\n\n");
        return clean.trim();
    }

    private List<String> tokenize(String text) {
        // Split into meaningful tokens (words, numbers, dates)
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("[A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜŒ]{2,}|\\d{1,2}[./-]\\d{2}[./-]\\d{4}|\\d{5,}|[A-Z]{2}\\d{6,}")
                .matcher(text.toUpperCase());
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    // ===================== DOCUMENT TYPE DETECTION =====================

    private String detectDocumentType(String raw, String clean) {
        String u = raw.toUpperCase();

        // Priority 1: Explicit titles
        if (containsAny(u, "CARTE NATIONALE D'IDENTITÉ", "CARTE NATIONALE D'IDENTITE", "NATIONAL IDENTITY CARD"))
            return "ID_CARD";
        if (containsAny(u, "PERMIS DE CONDUIRE", "DRIVING LICENCE", "DRIVING LICENSE"))
            return "DRIVER_LICENSE";
        if (containsAny(u, "PASSEPORT", "PASSPORT"))
            return "PASSPORT";

        // Priority 2: MRZ indicators
        if (u.contains("P<CMR") || u.contains("PPCMR"))
            return "PASSPORT";
        if (u.contains("I<CMR") || u.contains("ID<CMR"))
            return "ID_CARD";

        // Priority 3: Structural indicators
        if (containsAny(u, "NIC NUMBER", "NUMÉRO CNI", "IDENTIFIANT UNIQUE", "UNIQUE IDENTIFIER"))
            return "ID_CARD";
        if (u.contains("4A.") && u.contains("4B.") && u.contains("5."))
            return "DRIVER_LICENSE";

        // Priority 4: Cameroon presence with document indicators
        if (containsAny(u, "REPUBLIC OF CAMEROON", "RÉPUBLIQUE DU CAMEROUN", "REPUBLIQUE DU CAMEROUN")) {
            if (u.contains("NOM") && u.contains("PRÉNOMS"))
                return "ID_CARD";
        }

        return "UNKNOWN";
    }

    private boolean containsAny(String text, String... patterns) {
        for (String p : patterns) {
            if (text.contains(p))
                return true;
        }
        return false;
    }

    // ===================== SECURITY VALIDATION =====================

    private Map<String, Object> validateSecurityFeatures(String raw) {
        Map<String, Object> result = new HashMap<>();

        // Count images
        int imageCount = countOccurrences(raw, "<img");
        result.put("imageCount", imageCount);

        // Check for emblems (images near country name at top)
        String top = raw.substring(0, Math.min(raw.length(), 1500)).toUpperCase();
        boolean hasEmblems = (containsAny(top, "RÉPUBLIQUE DU CAMEROUN", "REPUBLIC OF CAMEROON",
                "REPUBLIQUE DU CAMEROUN"))
                && imageCount >= 1;
        result.put("hasEmblems", hasEmblems);

        // Count signatures (images near signature keywords)
        int sigCount = 0;
        String[] sigKeywords = { "SIGNATURE", "AUTORITÉ", "AUTHORITY", "DGSN", "TITULAIRE", "HOLDER", "BEARER" };
        for (String kw : sigKeywords) {
            if (Pattern.compile(kw + ".{0,200}<img", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(raw).find()) {
                sigCount++;
            }
        }
        result.put("signatureCount", sigCount);

        return result;
    }

    private int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx++;
        }
        return count;
    }

    // ===================== LABEL-BASED EXTRACTION =====================

    private void extractByLabels(List<String> lines, Map<String, String> fields) {
        Map<String, String[]> labelMap = new LinkedHashMap<>();
        labelMap.put("surname", new String[] { "NOM/SURNAME", "NOM / SURNAME", "NOM", "SURNAME", "1. NOM" });
        labelMap.put("givenNames", new String[] { "PRÉNOMS/GIVEN NAMES", "PRÉNOMS / GIVEN NAMES", "PRÉNOMS",
                "GIVEN NAMES", "2. PRÉNOMS" });
        labelMap.put("dateOfBirth",
                new String[] { "DATE DE NAISSANCE/DATE OF BIRTH", "DATE DE NAISSANCE", "DATE OF BIRTH" });
        labelMap.put("placeOfBirth",
                new String[] { "LIEU DE NAISSANCE/PLACE OF BIRTH", "LIEU DE NAISSANCE", "PLACE OF BIRTH" });
        labelMap.put("sex", new String[] { "SEXE/SEX", "SEXE / SEX", "SEXE", "SEX" });
        labelMap.put("height", new String[] { "TAILLE/HEIGHT", "TAILLE / HEIGHT", "TAILLE", "HEIGHT" });
        labelMap.put("occupation",
                new String[] { "PROFESSION/OCCUPATION", "PROFESSION / OCCUPATION", "PROFESSION", "OCCUPATION" });
        labelMap.put("issueDate", new String[] { "DATE DE DÉLIVRANCE", "DATE OF ISSUE", "DÉLIVRÉ LE", "DÉLIVRANCE" });
        labelMap.put("expiryDate", new String[] { "DATE D'EXPIRATION", "DATE OF EXPIRY", "EXPIRE LE", "EXPIRATION" });
        labelMap.put("documentNumber",
                new String[] { "IDENTIFIANT UNIQUE", "UNIQUE IDENTIFIER", "NIC NUMBER", "NUMÉRO CNI", "N° PERMIS" });

        for (int i = 0; i < lines.size(); i++) {
            String lineUpper = lines.get(i).toUpperCase();

            // Special handling for combined SEXE/SEX TAILLE/HEIGHT line
            if (lineUpper.contains("SEXE") && lineUpper.contains("TAILLE")) {
                // This is a combined line, look for M/F and height on next line
                if (i + 1 < lines.size()) {
                    String nextLine = lines.get(i + 1).trim();
                    // Pattern like "M 1,85" or "F 1.67"
                    java.util.regex.Matcher sexHeightMatcher = Pattern.compile("^([MF])\\s+([0-9][,.]\\d{2})")
                            .matcher(nextLine);
                    if (sexHeightMatcher.find()) {
                        fields.putIfAbsent("sex", sexHeightMatcher.group(1));
                        fields.putIfAbsent("height", sexHeightMatcher.group(2));
                        continue;
                    }
                }
            }

            for (Map.Entry<String, String[]> entry : labelMap.entrySet()) {
                String field = entry.getKey();
                if (fields.containsKey(field) && fields.get(field) != null)
                    continue;

                for (String label : entry.getValue()) {
                    if (lineUpper.contains(label)) {
                        String value = extractValueAfterLabel(lines, i, lineUpper, label, field);
                        if (value != null && !value.isEmpty() && !isLabel(value)) {
                            // Extra validation for sex field
                            if (field.equals("sex")) {
                                if (value.length() == 1 && (value.equals("M") || value.equals("F"))) {
                                    fields.put(field, value);
                                }
                            } else {
                                // For other fields, make sure we don't have just M or F
                                if (!(value.length() == 1 && (value.equals("M") || value.equals("F")))) {
                                    fields.put(field, value);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private String extractValueAfterLabel(List<String> lines, int lineIndex, String lineUpper, String label,
            String fieldName) {
        String line = lines.get(lineIndex);

        // Try same line after label
        int labelEnd = lineUpper.indexOf(label) + label.length();
        if (labelEnd < line.length()) {
            String rest = line.substring(labelEnd).replaceAll("^[\\s/:]+", "").trim();

            // For sex field, just get the first character if it's M or F
            if (fieldName.equals("sex")) {
                if (!rest.isEmpty() && (rest.charAt(0) == 'M' || rest.charAt(0) == 'F')) {
                    return String.valueOf(rest.charAt(0));
                }
            } else {
                // For other fields, clean up any leading M/F that might be sex data
                rest = rest.replaceAll("^[MF]\\s+", "").trim();
                if (!rest.isEmpty() && rest.length() > 1 && !isLabel(rest)) {
                    return rest;
                }
            }
        }

        // Try next lines
        for (int j = lineIndex + 1; j < Math.min(lines.size(), lineIndex + 4); j++) {
            String candidate = lines.get(j).trim();
            if (candidate.isEmpty())
                continue;
            if (isLabel(candidate))
                continue;

            // For sex field, look for standalone M or F
            if (fieldName.equals("sex")) {
                if (candidate.length() >= 1 && (candidate.charAt(0) == 'M' || candidate.charAt(0) == 'F')) {
                    return String.valueOf(candidate.charAt(0));
                }
            } else {
                // For other fields, skip if it's just M or F (sex data)
                if (candidate.length() == 1 && (candidate.equals("M") || candidate.equals("F"))) {
                    continue;
                }
                // Remove leading M/F if followed by space (sex contamination)
                candidate = candidate.replaceAll("^[MF]\\s+", "").trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean isLabel(String text) {
        String u = text.toUpperCase();
        String[] labels = { "NOM", "SURNAME", "PRÉNOMS", "GIVEN", "NAMES", "DATE", "BIRTH", "NAISSANCE",
                "LIEU", "PLACE", "SEX", "SEXE", "TAILLE", "HEIGHT", "PROFESSION", "OCCUPATION",
                "DÉLIVRANCE", "ISSUE", "EXPIRATION", "EXPIRY", "IDENTIFIANT", "IDENTIFIER",
                "UNIQUE", "NIC", "NUMBER", "AUTORITÉ", "AUTHORITY", "PÈRE", "FATHER", "MÈRE", "MOTHER" };
        for (String l : labels) {
            if (u.contains(l))
                return true;
        }
        return false;
    }

    // ===================== NUMBERED FIELD EXTRACTION =====================

    private void extractByNumberedFields(String text, String docType, Map<String, String> fields) {
        if (docType.equals("DRIVER_LICENSE")) {
            putIfMissing(fields, "surname",
                    findPattern(text, "(?:^|\\n)1\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
            putIfMissing(fields, "givenNames",
                    findPattern(text, "(?:^|\\n)2\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));

            String field3 = findPattern(text, "(?:^|\\n)3\\.\\s*([\\d.-]+)[,\\s]+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)");
            if (field3 == null) {
                putIfMissing(fields, "dateOfBirth", findPattern(text, "(?:^|\\n)3\\.\\s*([\\d.-]+)"));
            }

            putIfMissing(fields, "issueDate", findPattern(text, "4a\\.\\s*([\\d.-]+)"));
            putIfMissing(fields, "expiryDate", findPattern(text, "4b\\.\\s*([\\d.-]+)"));
            putIfMissing(fields, "authority", findPattern(text, "4c\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s.]+)"));
            putIfMissing(fields, "reference", findPattern(text, "4d\\.\\s*([A-Z0-9-]+)"));
            putIfMissing(fields, "documentNumber", findPattern(text, "5\\.\\s*([A-Z0-9-]+)"));
            putIfMissing(fields, "categories", findPattern(text, "9\\.\\s*([A-E0-9]+)"));
        }

        if (docType.equals("PASSPORT")) {
            putIfMissing(fields, "surname",
                    findPattern(text, "1\\.\\s*Nom\\s*/\\s*Surname\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
            putIfMissing(fields, "givenNames",
                    findPattern(text, "2\\.\\s*Prénoms\\s*/\\s*Given names\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
            putIfMissing(fields, "nationality",
                    findPattern(text, "3\\.\\s*Nationalité\\s*/\\s*Nationality\\s*\\n+([A-Z/\\s]+)"));
            putIfMissing(fields, "dateOfBirth",
                    findPattern(text, "4\\.\\s*Date de naissance\\s*/\\s*Date of birth\\s*\\n+([\\d./-]+)"));
            putIfMissing(fields, "sex", findPattern(text, "5\\.\\s*Sexe\\s*/\\s*Sex\\s*\\n*([MF])"));
            putIfMissing(fields, "placeOfBirth",
                    findPattern(text, "6\\.\\s*Lieu de naissance\\s*/\\s*Place of birth\\s*\\n+([A-Z\\s-]+)"));
            putIfMissing(fields, "issueDate",
                    findPattern(text, "7\\.\\s*Date de délivrance\\s*/\\s*Date of issue\\s*\\n+([\\d./-]+)"));
            putIfMissing(fields, "expiryDate",
                    findPattern(text, "8\\.\\s*Date d'expiration\\s*/\\s*Date of expiry\\s*\\n+([\\d./-]+)"));
            putIfMissing(fields, "occupation",
                    findPattern(text, "9\\.\\s*Profession\\s*/\\s*Occupation\\s*\\n+([A-Z\\s-]+)"));
            putIfMissing(fields, "documentNumber",
                    findPattern(text, "(?:No de passeport|Passport no\\.?)\\s*\\n*([A-Z]{2}\\d{6,8})"));
        }
    }

    // ===================== PATTERN DISCOVERY =====================

    private void extractByPatternDiscovery(String raw, String clean, List<String> tokens, String docType,
            Map<String, String> fields) {
        // Find all dates in the document
        List<String> allDates = findAllMatches(clean, DATE_PATTERN);
        log.info("Discovered {} dates: {}", allDates.size(), allDates);

        // Find document numbers
        if (fields.get("documentNumber") == null || fields.get("documentNumber").length() < 6) {
            // Try CNI long number (17-20 digits)
            String cniNum = findFirstMatch(raw, CNI_NUMBER_PATTERN);
            if (cniNum != null) {
                fields.put("documentNumber", cniNum);
                log.info("Discovered CNI number via pattern: {}", cniNum);
            } else {
                // Try AA pattern
                String aaNum = findFirstMatch(raw, CNI_AA_PATTERN);
                if (aaNum != null) {
                    fields.put("documentNumber", aaNum.replace(" ", ""));
                    log.info("Discovered AA number via pattern: {}", aaNum);
                } else if (docType.equals("PASSPORT")) {
                    String passNum = findFirstMatch(raw, PASSPORT_NUMBER);
                    if (passNum != null)
                        fields.put("documentNumber", passNum);
                } else if (docType.equals("DRIVER_LICENSE")) {
                    String licNum = findFirstMatch(raw, LICENSE_NUMBER);
                    if (licNum != null)
                        fields.put("documentNumber", licNum);
                }
            }
        }

        // Assign dates contextually
        if (allDates.size() >= 1 && fields.get("dateOfBirth") == null) {
            // Birth date is typically the first date found near name fields
            fields.put("dateOfBirth", allDates.get(0));
        }
        if (allDates.size() >= 2 && fields.get("issueDate") == null) {
            fields.put("issueDate", allDates.get(allDates.size() > 2 ? allDates.size() - 2 : 1));
        }
        if (allDates.size() >= 2 && fields.get("expiryDate") == null) {
            fields.put("expiryDate", allDates.get(allDates.size() - 1));
        }

        // Find names via pattern if missing
        if (fields.get("surname") == null) {
            // Look for uppercase words after NOM
            String afterNom = findPattern(clean,
                    "(?:NOM|SURNAME)[\\s/:]*\\n*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]{2,20})");
            if (afterNom != null && !isLabel(afterNom)) {
                fields.put("surname", afterNom.trim());
            }
        }

        if (fields.get("givenNames") == null) {
            String afterPrenoms = findPattern(clean,
                    "(?:PRÉNOMS|GIVEN NAMES)[\\s/:]*\\n*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]{2,30})");
            if (afterPrenoms != null && !isLabel(afterPrenoms)) {
                fields.put("givenNames", afterPrenoms.trim());
            }
        }
    }

    // ===================== MRZ PARSING =====================

    private void extractFromMRZ(String raw, Map<String, String> fields) {
        // Passport MRZ (2 lines of 44 characters)
        Pattern mrzPassport = Pattern.compile("P<CMR([A-Z<]+)<<([A-Z<]+)<*\\n*([A-Z0-9<]{44})", Pattern.MULTILINE);
        Matcher m = mrzPassport.matcher(raw.toUpperCase().replaceAll("\\s+", ""));
        if (m.find()) {
            String surname = m.group(1).replace("<", " ").trim();
            String givenNames = m.group(2).replace("<", " ").trim();
            String line2 = m.group(3);
            String docNum = line2.substring(0, 9).replace("<", "");

            putIfMissing(fields, "surname", surname);
            putIfMissing(fields, "givenNames", givenNames);
            putIfMissing(fields, "documentNumber", docNum);
            log.info("Extracted from MRZ: surname={}, givenNames={}, docNum={}", surname, givenNames, docNum);
        }

        // CNI MRZ (I<CMR format)
        Pattern mrzCni = Pattern.compile("I<CMR([A-Z0-9<]+)", Pattern.MULTILINE);
        Matcher mCni = mrzCni.matcher(raw.toUpperCase());
        if (mCni.find()) {
            String mrzLine = mCni.group(1);
            // Extract document number (typically 9-10 chars after country code)
            if (mrzLine.length() > 10) {
                String docNum = mrzLine.substring(0, 10).replace("<", "");
                putIfMissing(fields, "documentNumber", docNum);
            }
        }
    }

    // ===================== FIELD NORMALIZATION =====================

    private void normalizeFields(Map<String, String> fields) {
        fields.entrySet().forEach(e -> {
            if (e.getValue() != null) {
                String v = e.getValue();
                // Remove leading/trailing noise
                v = v.replaceAll("^[\\s*#\\-:]+", "").replaceAll("[\\s*#\\-:]+$", "");
                // Remove extra newlines
                v = v.split("\\n")[0].trim();

                // For sex field, normalize to single letter
                if (e.getKey().equals("sex")) {
                    if (v.length() >= 1 && (v.charAt(0) == 'M' || v.charAt(0) == 'F')) {
                        v = String.valueOf(v.charAt(0));
                    }
                } else {
                    // For other fields, remove leading M/F if it looks like sex contamination
                    if (v.length() > 2 && (v.startsWith("M ") || v.startsWith("F "))) {
                        v = v.substring(2).trim();
                    }
                }

                e.setValue(v);
            }
        });
    }

    // ===================== NAME BUILDING =====================

    private String buildHolderName(Map<String, String> fields) {
        String surname = fields.get("surname");
        String givenNames = fields.get("givenNames");

        if (surname != null && givenNames != null) {
            return surname.trim() + " " + givenNames.trim();
        }
        if (surname != null)
            return surname.trim();
        if (givenNames != null)
            return givenNames.trim();
        return "INCONNU";
    }

    // ===================== VALIDATION MESSAGE =====================

    private String buildValidationMessage(boolean valid, boolean expired, boolean emblems, String docType) {
        if (valid)
            return "Document valide";
        if (expired)
            return "Document expiré";
        if (!emblems)
            return "Document non authentifié (emblèmes manquants)";
        if (docType.equals("UNKNOWN"))
            return "Type de document non reconnu";
        return "Document invalide";
    }

    // ===================== CONFIDENCE CALCULATION =====================

    private double calculateAdvancedConfidence(Map<String, String> fields, Map<String, Object> security,
            String docType, LocalDate birth, LocalDate expiry) {
        double score = 0;

        // Core fields (weighted heavily)
        if (fields.get("surname") != null && fields.get("surname").length() > 2)
            score += 0.18;
        if (fields.get("givenNames") != null && fields.get("givenNames").length() > 2)
            score += 0.12;
        if (fields.get("documentNumber") != null && fields.get("documentNumber").length() >= 6)
            score += 0.25;

        // Dates
        if (birth != null)
            score += 0.10;
        if (expiry != null)
            score += 0.10;

        // Security
        if ((boolean) security.getOrDefault("hasEmblems", false))
            score += 0.10;
        if ((int) security.getOrDefault("signatureCount", 0) > 0)
            score += 0.08;

        // Type recognition
        if (!docType.equals("UNKNOWN"))
            score += 0.07;

        return Math.min(1.0, Math.max(0.0, score));
    }

    // ===================== ADDITIONAL FIELDS =====================

    private Map<String, String> buildAdditionalFields(Map<String, String> fields, Map<String, Object> security) {
        Map<String, String> add = new LinkedHashMap<>();
        List<String> core = Arrays.asList("surname", "givenNames", "dateOfBirth", "issueDate", "expiryDate",
                "documentNumber");

        fields.forEach((k, v) -> {
            if (!core.contains(k) && v != null && !v.isEmpty()) {
                add.put(k, v);
            }
        });

        add.put("security_emblems", String.valueOf(security.get("hasEmblems")));
        add.put("security_signatures", String.valueOf(security.get("signatureCount")));
        add.put("security_images", String.valueOf(security.get("imageCount")));

        return add;
    }

    // ===================== UTILITY METHODS =====================

    private void putIfMissing(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty() && (map.get(key) == null || map.get(key).isEmpty())) {
            map.put(key, value);
        }
    }

    private String findPattern(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(text);
        if (m.find())
            return m.group(1).trim();
        return null;
    }

    private String findFirstMatch(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (m.find())
            return m.group(1);
        return null;
    }

    private List<String> findAllMatches(String text, Pattern pattern) {
        List<String> matches = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            matches.add(m.group(1));
        }
        return matches;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 6)
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
}