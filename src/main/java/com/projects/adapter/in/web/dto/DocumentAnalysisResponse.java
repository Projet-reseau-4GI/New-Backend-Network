package com.projects.adapter.in.web.dto;

import jakarta.validation.constraints.*;
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
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAnalysisResponse {

    /**
     * Type of document analyzed.
     */
    @NotBlank(message = "Le type de document est requis")
    private String documentType;

    /**
     * Document number extracted from the parsed text.
     */
    @Size(min = 5, max = 30, message = "Numéro de document invalide")
    private String documentNumber;

    /**
     * Country that issued the document.
     */
    @NotBlank(message = "Le pays d'émission est requis")
    private String issuingCountry;

    /**
     * Full name of the document holder.
     */
    @NotBlank(message = "Le nom du titulaire est requis")
    private String holderName;

    /**
     * Date of birth of the document holder.
     */
    @Past(message = "La date de naissance doit être dans le passé")
    private LocalDate dateOfBirth;

    /**
     * Date when the document was issued.
     */
    @PastOrPresent(message = "La date d'émission ne peut pas être dans le futur")
    private LocalDate issueDate;

    /**
     * Date when the document expires.
     */
    private LocalDate expirationDate;

    /**
     * Indicates whether the document is currently valid.
     */
    @NotNull
    private Boolean isValid;

    /**
     * Human-readable validation message.
     * Examples: "Your document is valid", "Your document is invalid"
     */
    private String validationMessage;

    /**
     * Confidence score ranging from 0.0 to 1.0.
     * Lower values indicate more uncertainty in the extraction.
     */
    private Double confidenceScore;

    /**
     * Flag indicating if there are too many inconsistencies.
     * When true, the results should be treated with caution.
     */
    private Boolean hasUncertainty;

    /**
     * Additional extracted fields that may vary by document type.
     */
    private Map<String, String> additionalFields;

    /**
     * Raw extracted text from the parsing API.
     */
    private String rawExtractedText;
}
