package Projects.Network.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class OcrNormalizationService {
    public enum FieldType {
        NAME, DATE, SEX, HEIGHT, DOCUMENT_NUMBER, PLACE, OCCUPATION, OTHER
    }

    private static final Map<Character, Character> NUMBER_TO_LETTER = Map.of('0', 'O', '1', 'I', '5', 'S', '8', 'B');
    private static final Map<Character, Character> LETTER_TO_NUMBER = Map.of('O', '0', 'I', '1', 'l', '1', 'S', '5');

    public String normalize(String value, FieldType type) {
        if (value == null)
            return null;
        return switch (type) {
            case NAME -> value.toUpperCase().chars()
                    .mapToObj(c -> (char) c)
                    .map(c -> NUMBER_TO_LETTER.getOrDefault(c, c))
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString()
                    .replaceAll("[^A-Z\\s-]", "").trim();
            case DATE -> value.toUpperCase().chars()
                    .mapToObj(c -> (char) c)
                    .map(c -> LETTER_TO_NUMBER.getOrDefault(c, c))
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString()
                    .replaceAll("[^0-9./-]", "").replace("/", ".");
            default -> value.trim();
        };
    }
}
