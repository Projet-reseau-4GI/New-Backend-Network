package com.yowyob.flashshop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a document stored in the database.
 * Matches the 'documents' table in PostgreSQL.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Table("documents")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity {

    /**
     * Unique identifier for the document.
     */
    @Id
    @Column("id")
    private UUID id;

    /**
     * Type or category of the document.
     */
    @Column("piece_type")
    private String piece_type;

    /**
     * Identifier of the platform that owns the document.
     */
    @Column("platform_id")
    private Long platform_id;

    /**
     * Date and time when the document metadata was created.
     */
    @Column("upload_date")
    private LocalDateTime upload_date;

    /**
     * Current status of the document.
     */
    @Column("status")
    private String status;

    // Getters for compatibility with camelCase naming convention for methods
    public String getPieceType() {
        return piece_type;
    }

    public Long getPlatformId() {
        return platform_id;
    }

    public LocalDateTime getUploadDate() {
        return upload_date;
    }
}
