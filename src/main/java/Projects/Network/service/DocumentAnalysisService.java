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
                return extractCNI(text);
            case "DRIVER_LICENSE":
                return extractDriverLicense(text);
            case "PASSPORT":
                return extractPassport(text);
            default:
                // Fallback or generic extraction
                f.put("documentNumber", find(text, "\\b([A-Z]{2}\\d{6,7})\\b"));
                f.put("surname", find(text, "(?:Nom|Surname)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
                return f;
        }
    }

    private Map<String, String> extractCNI(String text) {
        Map<String, String> f = new HashMap<>();
        f.put("documentNumber", find(text, "NUMÉRO CNI\\s*/\\s*NIC NUMBER\\s*\\n+([A-Z0-9]+)"));
        f.put("surname", find(text, "NOM\\s*/\\s*SURNAME\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|PRÉNOMS)"));
        f.put("givenNames", find(text, "PRÉNOMS\\s*/\\s*GIVEN NAMES\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|\\d{9})"));
        f.put("dateOfBirth", find(text, "DATE DE NAISSANCE\\s*/\\s*DATE OF BIRTH\\s*\\n+([\\d.]+)"));
        f.put("placeOfBirth", find(text,
                "LIEU DE NAISSANCE\\s*/\\s*PLACE OF BIRTH\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|PROFESSION)"));
        f.put("sex", find(text, "SEXE\\s*/\\s*SEX\\s*\\n+([MF])"));
        f.put("expiryDate", find(text, "DATE D'EXPIRATION\\s*/\\s*DATE OF EXPIRY\\s*\\n+([\\d.]+)"));
        f.put("fatherName", find(text,
                "NOM DU PÈRE\\s*/\\s*FATHER'S NAME\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|NOM DE LA MÈRE)"));
        f.put("motherName",
                find(text, "NOM DE LA MÈRE\\s*/\\s*MOTHER'S NAME\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|LIEU)"));
        f.put("occupation", find(text, "PROFESSION\\s*/\\s*OCCUPATION\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|DATE)"));

        // Complex group for Issue Date and Height
        Pattern p = Pattern.compile("DATE OF ISSUE HEIGHT\\s*\\n+([\\d.]+)\\s+([\\d.]+\\s*m)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            f.put("issueDate", m.group(1));
            f.put("height", m.group(2));
        } else {
            f.put("issueDate", find(text, "DATE OF ISSUE HEIGHT\\s*\\n+([\\d.]+)"));
        }

        return f;
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
        return m.find() ? m.group(1).trim() : null;
    }

    private String detectType(String text) {
        String u = text.toUpperCase();
        if (u.contains("PASSEPORT") || u.contains("PASSPORT"))
            return "PASSPORT";
        if (u.contains("PERMIS") || u.contains("DRIVING LICENCE") || u.contains("DRIVING LICENSE"))
            return "DRIVER_LICENSE";
        if (u.contains("CNI") || u.contains("CARTE NATIONALE") || u.contains("NOM DU PÈRE"))
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
                "fatherName", "motherName", "categories", "reference", "authority" };
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