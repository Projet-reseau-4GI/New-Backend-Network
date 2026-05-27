package com.yowyob.flashshop.service;

import com.yowyob.flashshop.config.ReactiveTenantContext;
import com.yowyob.flashshop.model.VerificationLog;
import com.yowyob.flashshop.repository.VerificationLogRepository;
import com.yowyob.flashshop.dto.DocumentAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationLoggingService {

    private final VerificationLogRepository verificationLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Logs the outcome of a document verification into the verification_logs table.
     * The platform ID is automatically extracted from the Reactor Context.
     * 
     * @param docType e.g., "ID_CARD", "PASSPORT"
     * @param status  "ACCEPTED" or "REJECTED"
     * @param reason  the reason of rejection, or null if accepted
     * @return Mono of the saved VerificationLog
     */
    public Mono<VerificationLog> logVerification(String docType, String status, String reason, Double confidence,
            DocumentAnalysisResponse response, Integer processingTimeMs) {
        // Retrieve the current platform from context, set by ApiKeyAuthenticationFilter
        return ReactiveTenantContext.getPlatform()
                .flatMap(platform -> {
                    String additionalFieldsJson = null;
                    if (response != null && response.getAdditionalFields() != null
                            && !response.getAdditionalFields().isEmpty()) {
                        try {
                            additionalFieldsJson = objectMapper.writeValueAsString(response.getAdditionalFields());
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize additional fields", e);
                        }
                    }

                    VerificationLog logEntity = VerificationLog.builder()
                            .platform_id(platform.getId())
                            .date(LocalDateTime.now())
                            .doc_type(docType)
                            .status(status)
                            .reason(reason)
                            .confidence(confidence)
                            .processing_time_ms(processingTimeMs)
                            .document_number(response != null ? response.getDocumentNumber() : null)
                            .holder_name(response != null ? response.getHolderName() : null)
                            .date_of_birth(response != null && response.getDateOfBirth() != null
                                    ? response.getDateOfBirth().toString()
                                    : null)
                            .issue_date(response != null && response.getIssueDate() != null
                                    ? response.getIssueDate().toString()
                                    : null)
                            .expiry_date(response != null && response.getExpirationDate() != null
                                    ? response.getExpirationDate().toString()
                                    : null)
                            .additional_fields(additionalFieldsJson)
                            .build();
                    return verificationLogRepository.save(logEntity);
                });
    }
}
