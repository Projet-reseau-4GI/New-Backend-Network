package com.projects.domain.model;

import java.time.LocalDateTime;

/**
 * Pure domain entity for a verification log entry.
 * No framework annotations.
 */
public class VerificationLog {

    private Long id;
    private Long platformId;
    private LocalDateTime date;
    private String docType;
    private String status;
    private String reason;
    private Double confidence;
    private Integer processingTimeMs;
    private String documentNumber;
    private String holderName;
    private String dateOfBirth;
    private String issueDate;
    private String expiryDate;
    private String additionalFields;

    public VerificationLog() {}

    private VerificationLog(Builder b) {
        this.id = b.id;
        this.platformId = b.platformId;
        this.date = b.date;
        this.docType = b.docType;
        this.status = b.status;
        this.reason = b.reason;
        this.confidence = b.confidence;
        this.processingTimeMs = b.processingTimeMs;
        this.documentNumber = b.documentNumber;
        this.holderName = b.holderName;
        this.dateOfBirth = b.dateOfBirth;
        this.issueDate = b.issueDate;
        this.expiryDate = b.expiryDate;
        this.additionalFields = b.additionalFields;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private Long platformId;
        private LocalDateTime date;
        private String docType;
        private String status;
        private String reason;
        private Double confidence;
        private Integer processingTimeMs;
        private String documentNumber;
        private String holderName;
        private String dateOfBirth;
        private String issueDate;
        private String expiryDate;
        private String additionalFields;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder platformId(Long platformId) { this.platformId = platformId; return this; }
        public Builder date(LocalDateTime date) { this.date = date; return this; }
        public Builder docType(String docType) { this.docType = docType; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder confidence(Double confidence) { this.confidence = confidence; return this; }
        public Builder processingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; return this; }
        public Builder documentNumber(String documentNumber) { this.documentNumber = documentNumber; return this; }
        public Builder holderName(String holderName) { this.holderName = holderName; return this; }
        public Builder dateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; return this; }
        public Builder issueDate(String issueDate) { this.issueDate = issueDate; return this; }
        public Builder expiryDate(String expiryDate) { this.expiryDate = expiryDate; return this; }
        public Builder additionalFields(String additionalFields) { this.additionalFields = additionalFields; return this; }
        public VerificationLog build() { return new VerificationLog(this); }
    }

    public Long getId() { return id; }
    public Long getPlatformId() { return platformId; }
    public LocalDateTime getDate() { return date; }
    public String getDocType() { return docType; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Double getConfidence() { return confidence; }
    public Integer getProcessingTimeMs() { return processingTimeMs; }
    public String getDocumentNumber() { return documentNumber; }
    public String getHolderName() { return holderName; }
    public String getDateOfBirth() { return dateOfBirth; }
    public String getIssueDate() { return issueDate; }
    public String getExpiryDate() { return expiryDate; }
    public String getAdditionalFields() { return additionalFields; }

    public void setId(Long id) { this.id = id; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }
    public void setDate(LocalDateTime date) { this.date = date; }
    public void setDocType(String docType) { this.docType = docType; }
    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public void setProcessingTimeMs(Integer processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setIssueDate(String issueDate) { this.issueDate = issueDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public void setAdditionalFields(String additionalFields) { this.additionalFields = additionalFields; }
}
