package Projects.Network.service;

import Projects.Network.config.ReactiveTenantContext;
import Projects.Network.model.VerificationLog;
import Projects.Network.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationLoggingService {

    private final VerificationLogRepository verificationLogRepository;

    /**
     * Logs the outcome of a document verification into the verification_logs table.
     * The platform ID is automatically extracted from the Reactor Context.
     * 
     * @param docType e.g., "ID_CARD", "PASSPORT"
     * @param status "ACCEPTED" or "REJECTED"
     * @param reason the reason of rejection, or null if accepted
     * @return Mono of the saved VerificationLog
     */
    public Mono<VerificationLog> logVerification(String docType, String status, String reason, Double confidence) {
        // Retrieve the current platform from context, set by ApiKeyAuthenticationFilter
        return ReactiveTenantContext.getPlatform()
                .flatMap(platform -> {
                    VerificationLog log = VerificationLog.builder()
                            .platformId(platform.getId())
                            .date(LocalDateTime.now())
                            .docType(docType)
                            .status(status)
                            .reason(reason)
                            .confidence(confidence)
                            .build();
                    return verificationLogRepository.save(log);
                });
    }
}
