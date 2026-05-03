package yowyob.comops.api.file.adapter.out.persistence;

import yowyob.comops.api.common.adapter.out.persistence.PersistableEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(schema = "file_core", name = "stored_file")
public record StoredFileEntity(
        @Id UUID id,
        UUID tenantId,
        Instant createdAt,
        Instant updatedAt,
        UUID organizationId,
        UUID uploadedByUserId,
        String fileName,
        String contentType,
        long size,
        String storagePath) implements PersistableEntity {
}
