package Projects.Network.model;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("verification_logs")
public class VerificationLog {

    @Id
    private Long id;

    @Column("platform_id")
    private Long platformId;

    @Column("date")
    private LocalDateTime date;

    @Column("doc_type")
    private String docType;

    /**
     * Status of the verification: ACCEPTED or REJECTED
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
    private Integer processingTimeMs;

    @Column("document_number")
    private String documentNumber;

    @Column("holder_name")
    private String holderName;

    @Column("date_of_birth")
    private String dateOfBirth;

    @Column("issue_date")
    private String issueDate;

    @Column("expiry_date")
    private String expiryDate;

    @Column("additional_fields")
    private String additionalFields;
}
