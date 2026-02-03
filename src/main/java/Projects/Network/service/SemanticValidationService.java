package Projects.Network.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SemanticValidationService {
    public Map<String, String> validateAndClean(Map<String, String> fields) {
        Map<String, String> cleaned = new HashMap<>(fields);
        cleaned.entrySet().removeIf(e -> e.getValue() == null || e.getValue().length() < 2);
        // Basic rule: names shouldn't have numbers
        if (cleaned.containsKey("surname")) {
            String s = cleaned.get("surname");
            if (s.matches(".*\\d.*"))
                cleaned.remove("surname");
        }
        return cleaned;
    }
}
