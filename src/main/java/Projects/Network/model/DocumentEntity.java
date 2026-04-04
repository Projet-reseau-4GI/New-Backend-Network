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

    @Id
    @Column("id")
    private UUID id;

    /**
     * Type or category of the document.
     */
    @Column("piece_type")
    private String pieceType;

    /**
     * Identifier of the platform that owns the document.
     */
    @Column("platform_id")
    private Long platformId;

    /**
     * Date and time when the document metadata was created.
     */
    @Column("upload_date")
    private LocalDateTime uploadDate;

    /**
     * Current status of the document.
     */
    @Column("status")
    private String status;

}
