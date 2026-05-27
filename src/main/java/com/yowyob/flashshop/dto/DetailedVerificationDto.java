package com.yowyob.flashshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for detailed verification logs.
 * Includes all extracted fields from document analysis.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedVerificationDto {

    private Long id;
    private Long platform_id;
    private String platform_name;
    private LocalDateTime date;
    private String doc_type;
    private String status;
    private Double confidence;
    private Integer processing_time_ms;

    private String document_number;
    private String holder_name;
    private String date_of_birth;
    private String issue_date;
    private String expiry_date;
    private String additional_fields;

    // ─────────────────────────────────────────────────────────────────────────
    // COMPATIBILITY GETTERS (camelCase)
    // ─────────────────────────────────────────────────────────────────────────

    @JsonProperty("platformId")
    public Long getPlatformId() {
        return platform_id;
    }

    @JsonProperty("platformName")
    public String getPlatformName() {
        return platform_name;
    }

    @JsonProperty("docType")
    public String getDocType() {
        return doc_type;
    }

    @JsonProperty("processingTimeMs")
    public Integer getProcessingTimeMs() {
        return processing_time_ms;
    }

    @JsonProperty("documentNumber")
    public String getDocumentNumber() {
        return document_number;
    }

    @JsonProperty("holderName")
    public String getHolderName() {
        return holder_name;
    }

    @JsonProperty("dateOfBirth")
    public String getDateOfBirth() {
        return date_of_birth;
    }

    @JsonProperty("issueDate")
    public String getIssueDate() {
        return issue_date;
    }

    @JsonProperty("expiryDate")
    public String getExpiryDate() {
        return expiry_date;
    }

    @JsonProperty("additionalFields")
    public String getAdditionalFields() {
        return additional_fields;
    }
}
