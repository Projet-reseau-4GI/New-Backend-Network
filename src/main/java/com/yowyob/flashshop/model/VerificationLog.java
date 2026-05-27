package com.yowyob.flashshop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for keeping the history of verification operations per platform,
 * without actually saving the files involved.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("verification_logs")
public class VerificationLog {

    /**
     * Unique identifier for the verification log.
     */
    @Id
    private Long id;

    /**
     * Identifier of the platform that performed the verification.
     */
    @Column("platform_id")
    private Long platform_id;

    /**
     * Date and time of the verification.
     */
    @Column("date")
    private LocalDateTime date;

    /**
     * Type of document verified.
     */
    @Column("doc_type")
    private String doc_type;

    /**
     * Status of the verification: ACCEPTED or REJECTED.
     */
    @Column("status")
    private String status;

    /**
     * Optional reason if rejected or additional metadata.
     */
    @Column("reason")
    private String reason;

    /**
     * AI confidence score for this verification.
     */
    @Column("confidence")
    private Double confidence;

    /**
     * Time taken to process the document in milliseconds.
     */
    @Column("processing_time_ms")
    private Integer processing_time_ms;

    /**
     * Document number extracted.
     */
    @Column("document_number")
    private String document_number;

    /**
     * Full name of the holder extracted.
     */
    @Column("holder_name")
    private String holder_name;

    /**
     * Date of birth extracted.
     */
    @Column("date_of_birth")
    private String date_of_birth;

    /**
     * Issue date extracted.
     */
    @Column("issue_date")
    private String issue_date;

    /**
     * Expiry date extracted.
     */
    @Column("expiry_date")
    private String expiry_date;

    /**
     * JSON string of additional extracted fields.
     */
    @Column("additional_fields")
    private String additional_fields;

    // Getters for compatibility with camelCase naming convention for methods
    public Long getPlatformId() {
        return platform_id;
    }

    public String getDocType() {
        return doc_type;
    }

    public Integer getProcessingTimeMs() {
        return processing_time_ms;
    }

    public String getDocumentNumber() {
        return document_number;
    }

    public String getHolderName() {
        return holder_name;
    }

    public String getDateOfBirth() {
        return date_of_birth;
    }

    public String getIssueDate() {
        return issue_date;
    }

    public String getExpiryDate() {
        return expiry_date;
    }

    public String getAdditionalFields() {
        return additional_fields;
    }

    public void setPlatformId(Long platform_id) {
        this.platform_id = platform_id;
    }

    public void setDocType(String doc_type) {
        this.doc_type = doc_type;
    }

    public void setProcessingTimeMs(Integer processing_time_ms) {
        this.processing_time_ms = processing_time_ms;
    }

    public void setDocumentNumber(String document_number) {
        this.document_number = document_number;
    }

    public void setHolderName(String holder_name) {
        this.holder_name = holder_name;
    }

    public void setDateOfBirth(String date_of_birth) {
        this.date_of_birth = date_of_birth;
    }

    public void setIssueDate(String issue_date) {
        this.issue_date = issue_date;
    }

    public void setExpiryDate(String expiry_date) {
        this.expiry_date = expiry_date;
    }

    public void setAdditionalFields(String additional_fields) {
        this.additional_fields = additional_fields;
    }
}
