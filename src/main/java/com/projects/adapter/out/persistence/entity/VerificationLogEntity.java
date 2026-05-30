package com.projects.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC persistence entity for VerificationLog.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("verification_logs")
public class VerificationLogEntity {

    @Id private Long id;
    @Column("platform_id") private Long platformId;
    @Column("date") private LocalDateTime date;
    @Column("doc_type") private String docType;
    @Column("status") private String status;
    @Column("reason") private String reason;
    @Column("confidence") private Double confidence;
    @Column("processing_time_ms") private Integer processingTimeMs;
    @Column("document_number") private String documentNumber;
    @Column("holder_name") private String holderName;
    @Column("date_of_birth") private String dateOfBirth;
    @Column("issue_date") private String issueDate;
    @Column("expiry_date") private String expiryDate;
    @Column("additional_fields") private String additionalFields;
}
