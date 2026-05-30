package com.yowyob.flashshop.service;

import com.yowyob.flashshop.model.Platform;
import com.yowyob.flashshop.model.VerificationLog;
import com.yowyob.flashshop.repository.PlatformRepository;
import com.yowyob.flashshop.repository.VerificationLogRepository;
import com.yowyob.flashshop.utils.SecurityUtils;
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
 * - Higher traffic during business hours (8h–18h)
 * - Weekend dips
 * - ~75% acceptance rate
 * - Varied document types
 * - Realistic processing times
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeederService {

    private final PlatformRepository platform_repository;
    private final VerificationLogRepository verification_log_repository;
    private final BCryptPasswordEncoder password_encoder;

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

    /**
     * Seeds the database with demo data for a given number of days.
     *
     * @param days the number of days to generate data for
     * @return a Mono with a success message
     */
    public Mono<String> seedDemoData(int days) {
        log.info("Starting data seeder for {} days of demo data", days);
        return platform_repository.count()
                .flatMap(count -> {
                    Mono<Platform> platform_mono;
                    if (count == 0) {
                        platform_mono = createDemoPlatform();
                    } else {
                        platform_mono = platform_repository.findAll().next();
                    }
                    return platform_mono;
                })
                .flatMap(platform -> generateLogs(platform.getId(), days))
                .map(total -> "✅ Seeder finished: " + total + " verifications generated.");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Platform> createDemoPlatform() {
        String raw_key = UUID.randomUUID().toString();
        Platform demo = Platform.builder()
                .name("Demo Platform")
                .email("demo@verifid.com")
                .password_hash(password_encoder.encode("Demo@12345"))
                .api_key(SecurityUtils.hashApiKey(raw_key))
                .email_verified(true)
                .active(true)
                .reset_attempts(0)
                .created_at(LocalDateTime.now().minusDays(90))
                .updated_at(LocalDateTime.now())
                .build();
        return platform_repository.save(demo)
                .doOnSuccess(p -> log.info("Demo platform created with id={}", p.getId()));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Long> generateLogs(Long platform_id, int days) {
        Random rng = new Random(42); // fixed seed for reproducibility
        List<VerificationLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (int d = days; d >= 0; d--) {
            LocalDateTime day_start = now.minusDays(d).withHour(0).withMinute(0).withSecond(0);
            boolean is_weekend = day_start.getDayOfWeek().getValue() >= 6;

            // Base load: 5–40 verifications on weekdays, 1–10 on weekends
            int base_count = is_weekend
                    ? 1 + rng.nextInt(10)
                    : 5 + rng.nextInt(36);

            for (int v = 0; v < base_count; v++) {
                // Weight towards business hours (8–18)
                int hour = weightedHour(rng, is_weekend);
                int minute = rng.nextInt(60);
                int second = rng.nextInt(60);

                LocalDateTime log_date = day_start.withHour(hour).withMinute(minute).withSecond(second);
                String doc_type = DOC_TYPES[rng.nextInt(DOC_TYPES.length)];

                // ~75% acceptance rate (varies per doc type)
                boolean accepted = rng.nextDouble() < acceptanceRate(doc_type);
                String status = accepted ? "ACCEPTED" : "REJECTED";
                String reason = accepted ? null : REJECTION_REASONS[rng.nextInt(REJECTION_REASONS.length)];
                double confidence = accepted
                        ? 0.75 + rng.nextDouble() * 0.25 // 75–100%
                        : 0.30 + rng.nextDouble() * 0.40; // 30–70%
                int processing_ms = 800 + rng.nextInt(4200); // 800ms – 5s

                logs.add(VerificationLog.builder()
                        .platform_id(platform_id)
                        .date(log_date)
                        .doc_type(doc_type)
                        .status(status)
                        .reason(reason)
                        .confidence(Math.round(confidence * 10000.0) / 10000.0)
                        .processing_time_ms(processing_ms)
                        .build());
            }
        }

        log.info("Inserting {} verification logs for platform {}", logs.size(), platform_id);

        return verification_log_repository.saveAll(Flux.fromIterable(logs))
                .count()
                .doOnSuccess(n -> log.info("Inserted {} logs", n));
    }

    /** Weighted random hour: peaks at 9, 11, 14, 16 */
    private int weightedHour(Random rng, boolean is_weekend) {
        if (is_weekend)
            return 8 + rng.nextInt(14); // 8h–22h on weekends
        // Business hours distribution
        double r = rng.nextDouble();
        if (r < 0.15)
            return 8 + rng.nextInt(1); // 8h
        if (r < 0.30)
            return 9 + rng.nextInt(2); // 9–10h
        if (r < 0.45)
            return 11 + rng.nextInt(1); // 11h
        if (r < 0.55)
            return 12 + rng.nextInt(2); // 12–13h (lunch dip)
        if (r < 0.70)
            return 14 + rng.nextInt(2); // 14–15h
        if (r < 0.85)
            return 16 + rng.nextInt(2); // 16–17h
        return 18 + rng.nextInt(5); // 18–22h (off-hours)
    }

    /** Different doc types have different acceptance rates */
    private double acceptanceRate(String doc_type) {
        return switch (doc_type) {
            case "Passeport" -> 0.88;
            case "CNI" -> 0.80;
            case "Permis de conduire" -> 0.78;
            case "Titre de séjour" -> 0.70;
            case "Carte de résident" -> 0.72;
            case "Carte consulaire" -> 0.65;
            default -> 0.75;
        };
    }
}
