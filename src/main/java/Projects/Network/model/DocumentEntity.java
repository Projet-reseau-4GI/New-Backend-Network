package Projects.Network.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import java.util.UUID;

/**
 * Entity class representing a legal document record in the database.
 * This class maps to the "documents" table in PostgreSQL.
 */
@Data // Lombok annotation to automatically generate getters, setters, toString, equals, and hashCode
@Table("documents") // Specifies the name of the database table this entity maps to
public class DocumentEntity {

    /**
     * Primary key of the document.
     * Annotated with @Id to indicate it is the unique identifier for Spring Data R2DBC.
     */
    @Id
    @Column("document_id")
    private UUID documentId; // System-assigned unique ID (id-piece)

    /**
     * Category of the document.
     * Examples: PASSPORT, ID_CARD (CNI), DRIVER_LICENSE (permis).
     */
    @Column("document_type")
    private String documentType;

    /**
     * The logical name assigned to the file.
     * Example: "passport_John_Doe.pdf".
     */
    @Column("document_name")
    private String documentName;

    /**
     * The size of the uploaded file stored in bytes.
     */
    @Column("document_size")
    private Long documentSize;

    /**
     * The storage location reference.
     * Stores the full path or object key used to retrieve the file from MinIO.
     */
    @Column("minio_path")
    private String minioPath;

    /**
     * Foreign key linking this document to a specific user.
     * Represents the owner of the document (id-utilisateur).
     */
    @Column("user_id")
    private UUID userId;

    // Note: Manual setters are not required here because @Data provides them automatically.
}