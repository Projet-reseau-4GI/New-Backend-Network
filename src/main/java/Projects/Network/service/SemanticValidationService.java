package Projects.Network.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SemanticValidationService {
    private static final Set<String> SYSTEM_WORDS = Set.of("DATE", "NAME", "VALID", "TRUE", "FALSE", "NULL", "UNKNOWN",
            "CMR", "CAMEROON", "RÉPUBLIQUE", "REPUBLIC");

    private static final Pattern CNI_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{8,12}$");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z]{1,2}[0-9]{6,9}$");
    private static final Pattern PERMIT_PATTERN = Pattern.compile("^CE-\\d{6}-\\d{2}$");

    public Map<String, String> validateAndClean(Map<String, String> fields, String docType) {
        Map<String, String> cleaned = new HashMap<>(fields);

        // 1. Basic Cleaning and Label Rejection
        cleaned.entrySet().removeIf(
                e -> e.getValue() == null || e.getValue().trim().length() < 2 || isSystemWordOrLabel(e.getValue()));

        // 2. Name Validation
        validateNameField(cleaned, "surname");
        validateNameField(cleaned, "givenNames");

        // 3. Sex Validation
        if (cleaned.containsKey("sex")) {
            String sex = cleaned.get("sex").toUpperCase();
            if (!sex.equals("M") && !sex.equals("F")) {
                cleaned.remove("sex");
            } else {
                cleaned.put("sex", sex);
            }
        }

        // 4. Document Number Validation
        if (cleaned.containsKey("documentNumber")) {
            String num = cleaned.get("documentNumber").replaceAll("\\s", "");
            if (!isValidDocumentNumber(num, docType)) {
                cleaned.remove("documentNumber");
            } else {
                cleaned.put("documentNumber", num);
            }
        }

        // 5. Height Validation ("Taille")
        if (cleaned.containsKey("height")) {
            String h = cleaned.get("height").replaceAll("\\s", "");
            if (!h.matches("^[0-9]([,.]\\d{1,2})?m?$") && !h.matches("^\\d{2,3}(cm)?$")) {
                cleaned.remove("height");
            }
        }

        return cleaned;
    }

    private void validateNameField(Map<String, String> fields, String key) {
        if (!fields.containsKey(key))
            return;
        String val = fields.get(key);
        // Letters only, no numbers, length >= 2
        if (!val.matches("^[A-ZÀÂÄÇÈÉÊËÏÎÔÙÛÜŒ\\s-]+$") || val.length() < 2) {
            fields.remove(key);
        }
    }

    private boolean isSystemWordOrLabel(String text) {
        String u = text.toUpperCase().trim();
        if (SYSTEM_WORDS.contains(u))
            return true;
        // Check for common label patterns
        String[] labels = { "VALID FROM", "DEPUIS LE", "EXPIRY DATE", "DATE D'EXPIRATION", "ISSUED ON", "LE DGSN",
                "THE DGSN" };
        for (String l : labels) {
            if (u.contains(l))
                return true;
        }
        return false;
    }

    private boolean isValidDocumentNumber(String num, String docType) {
        if ("ID_CARD".equals(docType))
            return CNI_PATTERN.matcher(num).matches();
        if ("PASSPORT".equals(docType))
            return PASSPORT_PATTERN.matcher(num).matches();
        if ("DRIVER_LICENSE".equals(docType))
            return PERMIT_PATTERN.matcher(num).matches();
        return num.length() >= 6; // Fallback
    }

    public boolean validateDateConsistency(LocalDate birth, LocalDate issue, LocalDate expiry, String docType) {
        // Simplified: Primary rule is Expiry > Today
        // Issue date is optional for validity if Expiry is present
        LocalDate today = LocalDate.now();

        if (expiry != null && expiry.isBefore(today))
            return false;

        if (birth != null && birth.isAfter(today))
            return false;

        // Logical order if all are present
        if (birth != null && issue != null && !birth.isBefore(issue))
            return false;
        if (issue != null && expiry != null && !issue.isBefore(expiry))
            return false;
        if (birth != null && expiry != null && !birth.isBefore(expiry))
            return false;

        return true;
    }
}
