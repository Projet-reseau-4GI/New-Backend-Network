package Projects.Network.service;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
    private final OcrNormalizationService ocrNormalizationService;
    private final OcrHeuristicService ocrHeuristicService;
    private final SemanticValidationService semanticValidationService;
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
                    return Mono.zip(frontMono, backMono)
                            .flatMap(tuple -> analyzeFull(tuple.getT1(), tuple.getT2()));
                });
    }

    public Mono<DocumentAnalysisResponse> analyzeFull(String front, String back) {
        String combined = front + "\n" + back;
        return geminiService.extractData(combined)
                .map(geminiFields -> analyze(front, back, geminiFields));
    }

    private DocumentAnalysisResponse analyze(String front, String back, Map<String, String> geminiFields) {
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

        // Strategy A: Label-based extraction
        extractByLabels(lines, fields);

        // Strategy B: Type-specific numbered extraction
        extractByNumberedFields(cleanText, docType, fields);

        // Strategy C: Pattern discovery
        extractByPatternDiscovery(rawCombined, cleanText, tokens, docType, fields);

        // Strategy D: MRZ parsing
        extractFromMRZ(rawCombined, fields);

        // Strategy E: Gemini AI Refinement (Merge & Prioritize)
        String geminiDocType = geminiFields.get("documentType");
        if (geminiDocType != null && !geminiDocType.equals("UNKNOWN") && !geminiDocType.equals("null")) {
            log.info("Gemini suggested document type: {}", geminiDocType);
            docType = geminiDocType;
        }

        geminiFields.forEach((k, v) -> {
            if (v != null && !v.isBlank() && !v.equalsIgnoreCase("null") && !k.equals("documentType")) {
                // USER PRIORITY: Use Gemini for EVERYTHING it finds, especially if requested by
                // user.
                log.info("Gemini Overwrite: field {} = '{}'", k, v);
                fields.put(k, v);
            }
        });

        // PHASE 5: OCR NORMALIZATION
        applyOcrNormalization(fields);

        // PHASE 6: HEURISTIC CORRECTION
        applyHeuristicCorrections(fields);

        // PHASE 7: SEMANTIC VALIDATION
        Map<String, String> validatedFields = semanticValidationService.validateAndClean(fields, docType);

        // PHASE 8: BASIC NORMALIZATION
        normalizeFields(validatedFields);

        // PHASE 9: DATE PARSING
        LocalDate birthDate = parseDate(validatedFields.get("dateOfBirth"));
        LocalDate issueDate = parseDate(validatedFields.get("issueDate"));
        LocalDate expiryDate = parseDate(validatedFields.get("expiryDate"));

        // PHASE 10: NAME BUILDING
        String holderName = buildHolderName(validatedFields);

        // PHASE 11: VALIDITY DETERMINATION
        boolean namesValid = validatedFields.get("surname") != null && validatedFields.get("givenNames") != null;
        boolean datesIncoherent = !semanticValidationService.validateDateConsistency(birthDate, issueDate, expiryDate,
                docType);
        boolean isExpired = expiryDate != null && expiryDate.isBefore(LocalDate.now());
        boolean hasEmblems = (boolean) security.getOrDefault("hasEmblems", false);
        boolean hasDocNumber = validatedFields.get("documentNumber") != null;

        // Primary user rule: Document is valid if not expired
        boolean valid = !isExpired && namesValid && !docType.equals("UNKNOWN") && !datesIncoherent;
        String validationMessage = buildValidationMessage(valid, isExpired, datesIncoherent, namesValid, hasDocNumber,
                docType, hasEmblems);

        // PHASE 12: CONFIDENCE CALCULATION
        double confidence = calculateAdvancedConfidence(validatedFields, security, docType, birthDate, expiryDate);

        log.info("=== Analysis Complete: type={}, confidence={}, valid={} ===", docType, confidence, valid);

        DocumentAnalysisResponse response = DocumentAnalysisResponse.builder()
                .documentType(docType)
                .documentNumber(validatedFields.get("documentNumber"))
                .holderName(holderName)
                .dateOfBirth(birthDate)
                .issueDate(issueDate)
                .expirationDate(expiryDate)
                .isValid(valid)
                .validationMessage(validationMessage)
                .confidenceScore(confidence)
                .hasUncertainty(confidence < 0.6)
                .additionalFields(buildAdditionalFields(validatedFields, security))
                .rawExtractedText(rawCombined)
                .build();

        // PHASE 13: FORMAL BEAN VALIDATION
        Set<ConstraintViolation<DocumentAnalysisResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            log.warn("Formal validation found {} issues", violations.size());
            String formalSummary = violations.stream()
                    .map(v -> v.getMessage())
                    .distinct()
                    .collect(Collectors.joining(", "));
            response.setValidationMessage(response.getValidationMessage() + " (Format: " + formalSummary + ")");
            // If critical fields fail formal validation, we might want to force isValid to
            // false
            // but for now we just enrich the message.
        }

        return response;
    }

    // ===================== PRE-PROCESSING =====================

    private String preProcess(String raw) {
        String clean = raw.replaceAll("<[^>]+>", "\n");
        clean = clean.replaceAll("[ \\t]+", " ");
        clean = clean.replaceAll("\\n{3,}", "\n\n");
        return clean.trim();
    }

    private List<String> tokenize(String text) {
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
        if (containsAny(u, "CARTE NATIONALE D'IDENTITÉ", "CARTE NATIONALE D'IDENTITE", "NATIONAL IDENTITY CARD"))
            return "ID_CARD";
        if (containsAny(u, "PERMIS DE CONDUIRE", "DRIVING LICENCE", "DRIVING LICENSE"))
            return "DRIVER_LICENSE";
        if (containsAny(u, "PASSEPORT", "PASSPORT"))
            return "PASSPORT";
        if (u.contains("P<CMR") || u.contains("PPCMR"))
            return "PASSPORT";
        if (u.contains("I<CMR") || u.contains("ID<CMR"))
            return "ID_CARD";
        if (containsAny(u, "NIC NUMBER", "NUMÉRO CNI", "IDENTIFIANT UNIQUE", "UNIQUE IDENTIFIER"))
            return "ID_CARD";
        if (u.contains("4A.") && u.contains("4B.") && u.contains("5."))
            return "DRIVER_LICENSE";
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
        int imageCount = countOccurrences(raw, "<img");
        result.put("imageCount", imageCount);
        String top = raw.substring(0, Math.min(raw.length(), 1500)).toUpperCase();
        boolean hasEmblems = (containsAny(top, "RÉPUBLIQUE DU CAMEROUN", "REPUBLIC OF CAMEROON",
                "REPUBLIQUE DU CAMEROUN")) && imageCount >= 1;
        result.put("hasEmblems", hasEmblems);
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
            if (lineUpper.contains("SEXE") && lineUpper.contains("TAILLE")) {
                if (i + 1 < lines.size()) {
                    String nextLine = lines.get(i + 1).trim();
                    Matcher sexHeightMatcher = Pattern.compile("^([MF])\\s+([0-9][,.]\\d{2})").matcher(nextLine);
                    if (sexHeightMatcher.find()) {
                        fields.putIfAbsent("sex", sexHeightMatcher.group(1));
                        fields.putIfAbsent("height", sexHeightMatcher.group(2));
                        continue;
                    }
                }
            }
            for (Map.Entry<String, String[]> entry : labelMap.entrySet()) {
                String field = entry.getKey();
                if (fields.get(field) != null)
                    continue;
                for (String label : entry.getValue()) {
                    if (lineUpper.contains(label)) {
                        String value = extractValueAfterLabel(lines, i, lineUpper, label, field);
                        if (value != null && !value.isEmpty() && !isLabel(value)) {
                            fields.put(field, value);
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
        int labelEnd = lineUpper.indexOf(label) + label.length();
        if (labelEnd < line.length()) {
            String rest = line.substring(labelEnd).replaceAll("^[\\s/:]+", "").trim();
            if (fieldName.equals("sex")) {
                if (!rest.isEmpty() && (rest.charAt(0) == 'M' || rest.charAt(0) == 'F'))
                    return String.valueOf(rest.charAt(0));
            } else {
                rest = rest.replaceAll("^[MF]\\s+", "").trim();
                if (!rest.isEmpty() && rest.length() > 1 && !isLabel(rest))
                    return rest;
            }
        }
        for (int j = lineIndex + 1; j < Math.min(lines.size(), lineIndex + 4); j++) {
            String candidate = lines.get(j).trim();
            if (candidate.isEmpty() || isLabel(candidate))
                continue;
            if (fieldName.equals("sex")) {
                if (candidate.length() >= 1 && (candidate.charAt(0) == 'M' || candidate.charAt(0) == 'F'))
                    return String.valueOf(candidate.charAt(0));
            } else {
                if (candidate.length() == 1 && (candidate.equals("M") || candidate.equals("F")))
                    continue;
                candidate = candidate.replaceAll("^[MF]\\s+", "").trim();
                if (!candidate.isEmpty() && !isLabel(candidate))
                    return candidate;
            }
        }
        return null;
    }

    private boolean isLabel(String text) {
        String u = text.toUpperCase();
        String[] labels = { "NOM", "SURNAME", "PRÉNOMS", "GIVEN", "NAMES", "DATE", "BIRTH", "NAISSANCE", "LIEU",
                "PLACE", "SEX", "SEXE", "TAILLE", "HEIGHT", "PROFESSION", "OCCUPATION", "DÉLIVRANCE", "ISSUE",
                "EXPIRATION", "EXPIRY", "IDENTIFIANT", "IDENTIFIER", "UNIQUE", "NIC", "NUMBER" };
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
            putIfMissing(fields, "dateOfBirth", findPattern(text, "(?:^|\\n)3\\.\\s*([\\d.-]+)"));
            putIfMissing(fields, "issueDate", findPattern(text, "4a\\.\\s*([\\d.-]+)"));
            putIfMissing(fields, "expiryDate", findPattern(text, "4b\\.\\s*([\\d.-]+)"));
            putIfMissing(fields, "documentNumber", findPattern(text, "5\\.\\s*([A-Z0-9-]+)"));
        } else if (docType.equals("PASSPORT")) {
            putIfMissing(fields, "surname",
                    findPattern(text, "1\\.\\s*Nom\\s*/\\s*Surname\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
            putIfMissing(fields, "givenNames",
                    findPattern(text, "2\\.\\s*Prénoms\\s*/\\s*Given names\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
            putIfMissing(fields, "dateOfBirth",
                    findPattern(text, "4\\.\\s*Date de naissance\\s*/\\s*Date of birth\\s*\\n+([\\d./-]+)"));
            putIfMissing(fields, "documentNumber",
                    findPattern(text, "(?:No de passeport|Passport no\\.?)\\s*\\n*([A-Z]{2}\\d{6,8})"));
        }
    }

    private void extractByPatternDiscovery(String raw, String clean, List<String> tokens, String docType,
            Map<String, String> fields) {
        List<String> allDates = findAllMatches(clean, DATE_PATTERN);
        if (fields.get("documentNumber") == null) {
            String cniNum = findFirstMatch(raw, CNI_NUMBER_PATTERN);
            if (cniNum != null)
                fields.put("documentNumber", cniNum);
            else {
                String aaNum = findFirstMatch(raw, CNI_AA_PATTERN);
                if (aaNum != null)
                    fields.put("documentNumber", aaNum.replace(" ", ""));
            }
        }
        if (allDates.size() >= 1 && fields.get("dateOfBirth") == null)
            fields.put("dateOfBirth", allDates.get(0));
        if (allDates.size() >= 2 && fields.get("issueDate") == null)
            fields.put("issueDate", allDates.get(allDates.size() - 2));
        if (allDates.size() >= 2 && fields.get("expiryDate") == null)
            fields.put("expiryDate", allDates.get(allDates.size() - 1));
    }

    private void extractFromMRZ(String raw, Map<String, String> fields) {
        Pattern mrzPassport = Pattern.compile("P<CMR([A-Z<]+)<<([A-Z<]+)<*\\n*([A-Z0-9<]{44})", Pattern.MULTILINE);
        Matcher m = mrzPassport.matcher(raw.toUpperCase().replaceAll("\\s+", ""));
        boolean passportFound = m.find();
        if (passportFound) {
            putIfMissing(fields, "surname", m.group(1).replace("<", " ").trim());
            putIfMissing(fields, "givenNames", m.group(2).replace("<", " ").trim());
            putIfMissing(fields, "documentNumber", m.group(3).substring(0, 9).replace("<", ""));
        }
        Pattern mrzCni = Pattern.compile("I<CMR([A-Z0-9<]+)", Pattern.MULTILINE);
        Matcher mCni = mrzCni.matcher(raw.toUpperCase());
        if (mCni.find() && mCni.group(1).length() > 10) {
            putIfMissing(fields, "documentNumber", mCni.group(1).substring(0, 10).replace("<", ""));
        }

        // Priority MRZ overwrite for names if MRZ is high quality
        if (passportFound) {
            fields.put("surname", m.group(1).replace("<", " ").trim());
            fields.put("givenNames", m.group(2).replace("<", " ").trim());
        }
    }

    private void applyOcrNormalization(Map<String, String> fields) {
        if (fields.get("surname") != null)
            fields.put("surname",
                    ocrNormalizationService.normalize(fields.get("surname"), OcrNormalizationService.FieldType.NAME));
        if (fields.get("givenNames") != null)
            fields.put("givenNames", ocrNormalizationService.normalize(fields.get("givenNames"),
                    OcrNormalizationService.FieldType.NAME));
        if (fields.get("dateOfBirth") != null)
            fields.put("dateOfBirth", ocrNormalizationService.normalize(fields.get("dateOfBirth"),
                    OcrNormalizationService.FieldType.DATE));
    }

    private void applyHeuristicCorrections(Map<String, String> fields) {
        if (fields.get("placeOfBirth") != null)
            fields.put("placeOfBirth", ocrHeuristicService.correctPlaceOfBirth(fields.get("placeOfBirth")));
        if (fields.get("occupation") != null)
            fields.put("occupation", ocrHeuristicService.correctOccupation(fields.get("occupation")));
    }

    private void normalizeFields(Map<String, String> fields) {
        fields.entrySet().forEach(e -> {
            if (e.getValue() != null) {
                String v = e.getValue().replaceAll("^[\\s*#\\-:]+", "").replaceAll("[\\s*#\\-:]+$", "").split("\\n")[0]
                        .trim();
                if (e.getKey().equals("sex") && v.length() >= 1 && (v.charAt(0) == 'M' || v.charAt(0) == 'F'))
                    v = String.valueOf(v.charAt(0));
                e.setValue(v);
            }
        });
    }

    private String buildHolderName(Map<String, String> fields) {
        String s = fields.get("surname"), g = fields.get("givenNames");
        if (s != null && g != null)
            return s.trim() + " " + g.trim();
        return s != null ? s.trim() : (g != null ? g.trim() : "INCONNU");
    }

    private String buildValidationMessage(boolean valid, boolean expired, boolean datesIncoherent, boolean namesValid,
            boolean hasDocNumber, String docType, boolean hasEmblems) {
        if (valid)
            return "Document valide et cohérent";
        if (docType.equals("UNKNOWN"))
            return "Type de document non reconnu";
        if (!namesValid)
            return "Nom ou prénoms invalides ou illisibles";
        if (!hasDocNumber)
            return "Numéro de document invalide ou manquant";
        if (expired)
            return "Document expiré";
        if (datesIncoherent)
            return "Incohérence des dates (Naissance/Émission/Expiration)";
        if (!hasEmblems)
            return "Authenticité non confirmée (emblèmes manquants)";
        return "Document rejeté pour non-conformité métier";
    }

    private double calculateAdvancedConfidence(Map<String, String> fields, Map<String, Object> security, String docType,
            LocalDate birth, LocalDate expiry) {
        double score = 0;
        if (fields.get("surname") != null)
            score += 0.2;
        if (fields.get("documentNumber") != null)
            score += 0.3;
        if (birth != null)
            score += 0.15;
        if (expiry != null)
            score += 0.15;
        if ((boolean) security.getOrDefault("hasEmblems", false))
            score += 0.2;
        return Math.min(1.0, score);
    }

    private Map<String, String> buildAdditionalFields(Map<String, String> fields, Map<String, Object> security) {
        Map<String, String> add = new LinkedHashMap<>();
        // Filter out fields already present in the main response
        Set<String> topLevel = Set.of("surname", "givenNames", "documentNumber", "dateOfBirth", "issueDate",
                "expiryDate", "expirationDate");
        fields.forEach((k, v) -> {
            if (v != null && !v.isEmpty() && !topLevel.contains(k))
                add.put(k, v);
        });
        add.put("security_emblems", String.valueOf(security.get("hasEmblems")));
        return add;
    }

    private void putIfMissing(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty() && map.get(key) == null)
            map.put(key, value);
    }

    private String findPattern(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String findFirstMatch(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private List<String> findAllMatches(String text, Pattern pattern) {
        List<String> matches = new ArrayList<>();
        Matcher m = pattern.matcher(text);
        while (m.find())
            matches.add(m.group(1));
        return matches;
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
}