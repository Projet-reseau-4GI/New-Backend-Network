package Projects.Network.service;

import Projects.Network.model.Platform;
import Projects.Network.model.VerificationLog;
import Projects.Network.repository.PlatformRepository;
import Projects.Network.repository.VerificationLogRepository;
import Projects.Network.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service that generates realistic demo data for the dashboard.
 * Creates 1 demo platform (if none exist) then generates 90 days
 * of verification logs with realistic patterns:
 *   - Higher traffic during business hours (8h–18h)
 *   - Weekend dips
 *   - ~75% acceptance rate
 *   - Varied document types
 *   - Realistic processing times
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeederService {

    private final PlatformRepository platformRepository;
    private final VerificationLogRepository verificationLogRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final String[] DOC_TYPES = {
        "CNI", "Passeport", "Permis de conduire", "Titre de séjour",
        "Carte de résident", "Carte consulaire"
    };

    private static final String[] REJECTION_REASONS = {
        "Document expiré",
        "Image floue ou illisible",
        "Document altéré ou falsifié",
        "Informations non conformes",
        "Document non reconnu"
    };

    public Mono<String> seedDemoData(int days) {
        log.info("Starting data seeder for {} days of demo data", days);
        return platformRepository.count()
            .flatMap(count -> {
                Mono<Platform> platformMono;
                if (count == 0) {
                    platformMono = createDemoPlatform();
                } else {
                    platformMono = platformRepository.findAll().next();
                }
                return platformMono;
            })
            .flatMap(platform -> generateLogs(platform.getId(), days))
            .map(total -> "✅ Seeder terminé : " + total + " vérifications générées.");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Platform> createDemoPlatform() {
        String rawKey   = UUID.randomUUID().toString();
        Platform demo = Platform.builder()
            .name("Demo Platform")
            .email("demo@verifid.com")
            .passwordHash(passwordEncoder.encode("Demo@12345"))
            .apiKey(SecurityUtils.hashApiKey(rawKey))
            .emailVerified(true)
            .active(true)
            .resetAttempts(0)
            .createdAt(LocalDateTime.now().minusDays(90))
            .updatedAt(LocalDateTime.now())
            .build();
        return platformRepository.save(demo)
            .doOnSuccess(p -> log.info("Demo platform created with id={}", p.getId()));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Long> generateLogs(Long platformId, int days) {
        Random rng = new Random(42); // fixed seed for reproducibility
        List<VerificationLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int d = days; d >= 0; d--) {
            LocalDateTime dayStart = now.minusDays(d).withHour(0).withMinute(0).withSecond(0);
            boolean isWeekend = dayStart.getDayOfWeek().getValue() >= 6;

            // Base load: 5–40 verifications on weekdays, 1–10 on weekends
            int baseCount = isWeekend
                ? 1 + rng.nextInt(10)
                : 5 + rng.nextInt(36);

            for (int v = 0; v < baseCount; v++) {
                // Weight towards business hours (8–18)
                int hour = weightedHour(rng, isWeekend);
                int minute  = rng.nextInt(60);
                int second  = rng.nextInt(60);

                LocalDateTime logDate = dayStart.withHour(hour).withMinute(minute).withSecond(second);
                String docType = DOC_TYPES[rng.nextInt(DOC_TYPES.length)];

                // ~75% acceptance rate (varies per doc type)
                boolean accepted = rng.nextDouble() < acceptanceRate(docType);
                String status = accepted ? "ACCEPTED" : "REJECTED";
                String reason = accepted ? null : REJECTION_REASONS[rng.nextInt(REJECTION_REASONS.length)];
                double confidence = accepted
                    ? 0.75 + rng.nextDouble() * 0.25   // 75–100%
                    : 0.30 + rng.nextDouble() * 0.40;  // 30–70%
                int processingMs = 800 + rng.nextInt(4200); // 800ms – 5s

                logs.add(VerificationLog.builder()
                    .platformId(platformId)
                    .date(logDate)
                    .docType(docType)
                    .status(status)
                    .reason(reason)
                    .confidence(Math.round(confidence * 10000.0) / 10000.0)
                    .processingTimeMs(processingMs)
                    .build());
            }
        }

        log.info("Inserting {} verification logs for platform {}", logs.size(), platformId);

        return verificationLogRepository.saveAll(Flux.fromIterable(logs))
            .count()
            .doOnSuccess(n -> log.info("Inserted {} logs", n));
    }

    /** Weighted random hour: peaks at 9, 11, 14, 16 */
    private int weightedHour(Random rng, boolean isWeekend) {
        if (isWeekend) return 8 + rng.nextInt(14); // 8h–22h on weekends
        // Business hours distribution
        double r = rng.nextDouble();
        if (r < 0.15) return 8  + rng.nextInt(1);  // 8h
        if (r < 0.30) return 9  + rng.nextInt(2);  // 9–10h
        if (r < 0.45) return 11 + rng.nextInt(1);  // 11h
        if (r < 0.55) return 12 + rng.nextInt(2);  // 12–13h (lunch dip)
        if (r < 0.70) return 14 + rng.nextInt(2);  // 14–15h
        if (r < 0.85) return 16 + rng.nextInt(2);  // 16–17h
        return 18 + rng.nextInt(5);                 // 18–22h (off-hours)
    }

    /** Different doc types have different acceptance rates */
    private double acceptanceRate(String docType) {
        return switch (docType) {
            case "Passeport"           -> 0.88;
            case "CNI"                 -> 0.80;
            case "Permis de conduire"  -> 0.78;
            case "Titre de séjour"     -> 0.70;
            case "Carte de résident"   -> 0.72;
            case "Carte consulaire"    -> 0.65;
            default                    -> 0.75;
        };
    }
}
