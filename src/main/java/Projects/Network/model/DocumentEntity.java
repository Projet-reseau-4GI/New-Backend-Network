package Projects.Network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.http.codec.ServerSentEvent;

import java.util.UUID;
import java.time.LocalDateTime;

/**
 * DocumentEntity
 *
 * This entity represents a legal document stored in the database.
 * It is mapped to the "documents" table in PostgreSQL using Spring Data R2DBC.
 *
 * The purpose of this class is to define the structure of the persisted data
 * related to user documents such as passports, ID cards, or driver licenses.
 *
 * This class belongs to the Model (Entity) layer and must not contain
 * any business logic. It is only responsible for data representation
 * and persistence mapping.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Data
@Table("documents")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity {

    /**
     * Unique identifier of the document.
     *
     * This field represents the primary key of the "documents" table.
     * The @Id annotation marks it as the identifier managed by
     * Spring Data R2DBC.
     */
    @Id
    @Column("id")
    private UUID id;

    /**
     * Type or category of the document.
     *
     * This field describes the nature of the document stored.
     * Typical values include PASSPORT, ID_CARD, or DRIVER_LICENSE.
     */
    @Column("piece_type")
    private String pieceType;

    /**
     * Logical name of the stored file.
     *
     * This value usually corresponds to the original filename
     * provided by the user at upload time.
     */
    @Column("file_name")
    private String fileName;

    /**
     * Size of the stored file expressed in bytes.
     *
     * This information can be used for validation, monitoring
     * storage usage, or enforcing file size limits.
     */
    @Column("file_size")
    private Long fileSize;

    /**
     * Path or object key used to locate the file in the storage system.
     *
     * This value references the physical location of the file
     * in MinIO or any compatible object storage service.
     */
    @Column("minio_path")
    private String minioPath;

    /**
     * Backup path or alternative object key for the file in the storage system.
     *
     * This value references an alternative or backup location of the file
     * in MinIO or any compatible object storage service. It can be used
     * for redundancy, versioning, or backup purposes.
     */
    @Column("back_minio_path")
    private String backMinioPath;

    /**
     * Identifier of the user who owns the document.
     *
     * This field acts as a foreign key linking the document
     * to a specific user in the system.
     */
    @Column("user_id")
    private UUID userId;

    /**
     * MIME type of the stored file.
     *
     * Examples include "application/pdf", "image/png", or "image/jpeg".
     * This information is useful for validation and content handling.
     */
    @Column("file_type")
    private String fileType;

    /**
     * Date and time when the document was uploaded.
     *
     * This timestamp is typically set at upload time and
     * can be used for auditing or document lifecycle management.
     */
    @Column("upload_date")
    private LocalDateTime uploadDate;

    /**
     * Current status of the document.
     *
     * This field represents the validation state of the document,
     * such as UPLOADED, VERIFIED, or REJECTED.
     */
    @Column("status")
    private String status;

}
