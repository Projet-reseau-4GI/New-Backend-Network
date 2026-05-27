package com.yowyob.flashshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * DocumentAnalysisResponse
 *
 * Response DTO containing the analysis results of a document.
 * Includes extracted fields, validation status, and confidence indicators.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResponse {

    /**
     * Type of document analyzed.
     */
    @NotBlank(message = "Document type is required")
    private String document_type;

    /**
     * Document number extracted from the parsed text.
     */
    @Size(min = 5, max = 30, message = "Invalid document number")
    private String document_number;

    /**
     * Country that issued the document.
     */
    @NotBlank(message = "Issuing country is required")
    private String issuing_country;

    /**
     * Full name of the document holder.
     */
    @NotBlank(message = "Holder name is required")
    private String holder_name;

    /**
     * Date of birth of the document holder.
     */
    @Past(message = "Date of birth must be in the past")
    private LocalDate date_of_birth;

    /**
     * Date when the document was issued.
     */
    @PastOrPresent(message = "Issue date cannot be in the future")
    private LocalDate issue_date;

    /**
     * Date when the document expires.
     */
    private LocalDate expiration_date;

    /**
     * Indicates whether the document is currently valid.
     */
    @NotNull
    private Boolean is_valid;

    /**
     * Human-readable validation message.
     */
    private String validation_message;

    /**
     * Confidence score ranging from 0.0 to 1.0.
     */
    private Double confidence_score;

    /**
     * Flag indicating if there are too many inconsistencies.
     */
    private Boolean has_uncertainty;

    /**
     * Additional extracted fields that may vary by document type.
     */
    private Map<String, String> additional_fields;

    /**
     * Raw extracted text from the parsing API.
     */
    private String raw_extracted_text;

    // Custom getters to maintain pure camelCase method naming as per charter
    public String getDocumentType() {
        return document_type;
    }

    public String getDocumentNumber() {
        return document_number;
    }

    public String getIssuingCountry() {
        return issuing_country;
    }

    public String getHolderName() {
        return holder_name;
    }

    public LocalDate getDateOfBirth() {
        return date_of_birth;
    }

    public LocalDate getIssueDate() {
        return issue_date;
    }

    public LocalDate getExpirationDate() {
        return expiration_date;
    }

    public Boolean getIsValid() {
        return is_valid;
    }

    public String getValidationMessage() {
        return validation_message;
    }

    public Double getConfidenceScore() {
        return confidence_score;
    }

    public Boolean getHasUncertainty() {
        return has_uncertainty;
    }

    public Map<String, String> getAdditionalFields() {
        return additional_fields;
    }

    public String getRawExtractedText() {
        return raw_extracted_text;
    }
}
