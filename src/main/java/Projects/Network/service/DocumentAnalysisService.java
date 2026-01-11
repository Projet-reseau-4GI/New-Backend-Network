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
        
        // PERMIS - Logique spéciale avec légende
        if ("DRIVER_LICENSE".equals(docType)) {
            return extractDriverLicense(text);
        }
        
        // PASSEPORT - Extraction normale
        f.put("documentNumber", find(text, "(?:No de passeport|Passport no)\\s*\\n?\\s*([A-Z]{2}\\d{6,7})"));
        if (f.get("documentNumber") == null) f.put("documentNumber", find(text, "\\b([A-Z]{2}\\d{6,7})\\b"));
        
        f.put("surname", find(text, "(?:1\\.\\s*)?(?:Nom|Surname)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|2\\.)"));
        f.put("givenNames", find(text, "(?:2\\.\\s*)?(?:Prénoms|Given names)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+?)(?=\\n|3\\.)"));
        f.put("nationality", find(text, "(?:3\\.\\s*)?(?:Nationalité|Nationality)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ/\\s-]+?)(?=\\n|4\\.)"));
        f.put("dateOfBirth", find(text, "(?:4\\.\\s*)?(?:Date de naissance|Date of birth)\\s*\\n+([\\d./-]+)"));
        f.put("sex", find(text, "(?:5\\.\\s*)?(?:Sexe|Sex)\\s*\\n+([MF])"));
        f.put("placeOfBirth", find(text, "(?:6\\.\\s*)?(?:Lieu de naissance|Place of birth)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][\\w\\s-]+?)(?=\\n|7\\.)"));
        f.put("issueDate", find(text, "(?:7\\.\\s*)?(?:Date de délivrance|Date of Issue)\\s*\\n+([\\d./-]+)"));
        f.put("expiryDate", find(text, "(?:8\\.\\s*)?(?:Date d'expiration|Date of expiry)\\s*\\n+([\\d./-]+)"));
        f.put("occupation", find(text, "(?:9\\.\\s*)?(?:Profession|Occupation)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][\\w\\s-]+?)(?=\\n|10\\.)"));
        
        String heightCAN = find(text, "([\\d.,]+\\s*m)\\s+(\\d{5,})");
        if (heightCAN != null) {
            Pattern p = Pattern.compile("([\\d.,]+\\s*m)\\s+(\\d{5,})");
            Matcher m = p.matcher(heightCAN);
            if (m.find()) {
                f.put("height", m.group(1));
                f.put("canNumber", m.group(2));
            }
        }
        
        f.put("placeOfIssue", find(text, "(?:12\\.\\s*)?(?:Lieu de délivrance|Place of issue)\\s*\\n+([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ][\\w\\s-]+?)(?=\\n|13\\.)"));
        f.put("documentType", find(text, "Type\\s*/\\s*Type\\s*\\n+([A-Z]{1,3})"));
        f.put("countryCode", find(text, "(?:Code du pays|Country code)\\s*\\n+([A-Z]{3})"));
        f.put("mrz", find(text, "(PP[A-Z]{3}[A-Z<]+<<[A-Z<]+[A-Z0-9<]+)"));
        
        return f;
    }
    
    /**
     * Extraction spéciale PERMIS avec mapping légende
     */
    private Map<String, String> extractDriverLicense(String text) {
        Map<String, String> f = new HashMap<>();
        
        // Extraction directe avec numéros
        f.put("surname", find(text, "1\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
        f.put("givenNames", find(text, "2\\.\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)"));
        
        // Date et lieu (format: "11-01-2005, YAOUNDE")
        String dateLieu = find(text, "3\\.\\s*([\\d-]+),\\s*([A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜ\\s-]+)");
        if (dateLieu != null) {
            Pattern p = Pattern.compile("([\\d-]+),\\s*([A-Z]+)");
            Matcher m = p.matcher(dateLieu);
            if (m.find()) {
                f.put("dateOfBirth", m.group(1));
                f.put("placeOfBirth", m.group(2));
            }
        }
        
        f.put("issueDate", find(text, "4a\\.\\s*([\\d-]+)"));
        f.put("expiryDate", find(text, "4b\\.\\s*([\\d-]+)"));
        f.put("authority", find(text, "4c\\.\\s*([A-Z\\.\\s]+)"));
        f.put("documentNumber", find(text, "5\\.\\s*([A-Z0-9-]+)"));
        f.put("categories", find(text, "9\\.\\s*([A-E]+)"));
        
        return f;
    }

    private String find(String text, String pattern) {
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String detectType(String text) {
        String u = text.toUpperCase();
        if (u.contains("PASSEPORT") || u.contains("PASSPORT")) return "PASSPORT";
        if (u.contains("CNI") || u.contains("CARTE NATIONALE")) return "ID_CARD";
        if (u.contains("PERMIS")) return "DRIVER_LICENSE";
        return "UNKNOWN";
    }

    private String buildName(String surname, String given) {
        if (surname != null && given != null) return surname + " " + given;
        return surname != null ? surname : given;
    }

    private Map<String, String> buildAdditional(Map<String, String> fields) {
        Map<String, String> add = new HashMap<>();
        String[] keys = {"nationality", "sex", "placeOfBirth", "occupation", "height", 
                        "canNumber", "placeOfIssue", "documentType", "countryCode", "mrz"};
        for (String k : keys) {
            if (fields.get(k) != null) add.put(k, fields.get(k));
        }
        return add;
    }

    private LocalDate parseDate(String date) {
        if (date == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try { return LocalDate.parse(date, fmt); } 
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private double calcConfidence(Map<String, String> fields) {
        long count = fields.values().stream().filter(v -> v != null && !v.isEmpty()).count();
        return Math.min(1.0, count / 13.0);
    }
}