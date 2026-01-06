package Projects.Network.dto;

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
     * Possible values: PASSPORT, ID_CARD, DRIVER_LICENSE
     */
    private String documentType;

    /**
     * Document number extracted from the parsed text.
     */
    private String documentNumber;

    /**
     * Full name of the document holder.
     */
    private String holderName;

    /**
     * Date of birth of the document holder.
     */
    private LocalDate dateOfBirth;

    /**
     * Date when the document was issued.
     */
    private LocalDate issueDate;

    /**
     * Date when the document expires.
     */
    private LocalDate expirationDate;

    /**
     * Indicates whether the document is currently valid (not expired).
     */
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
