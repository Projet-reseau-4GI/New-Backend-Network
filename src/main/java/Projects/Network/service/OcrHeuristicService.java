package Projects.Network.service;

import org.springframework.stereotype.Service;

@Service
public class OcrHeuristicService {
    public String correctPlaceOfBirth(String raw) {
        if (raw == null)
            return null;
        String u = raw.toUpperCase();
        if (u.contains("DOU") || u.contains("DOV"))
            return "Douala";
        if (u.contains("YAO"))
            return "Yaoundé";
        if (u.contains("BAF"))
            return "Bafoussam";
        return raw;
    }

    public String correctOccupation(String raw) {
        if (raw == null)
            return null;
        String u = raw.toUpperCase();
        if (u.contains("ETU"))
            return "Etudiant";
        if (u.contains("ING"))
            return "Ingénieur";
        if (u.contains("MED"))
            return "Médecin";
        return raw;
    }
}
