package yowyob.comops.api.file.domain.model;

import yowyob.comops.api.common.domain.model.BaseEntity;
import java.time.Instant;
import java.util.UUID;

public final class StoredFile extends BaseEntity {

    private final UUID organizationId;
    private final UUID uploadedByUserId;
    private final String fileName;
    private final String contentType;
    private final long size;
    private final String storagePath;

    private StoredFile(UUID id, UUID tenantId, Instant createdAt, Instant updatedAt, UUID organizationId,
            UUID uploadedByUserId, String fileName, String contentType, long size, String storagePath) {
        super(id, tenantId, createdAt, updatedAt);
        this.organizationId = organizationId;
        this.uploadedByUserId = uploadedByUserId;
        this.fileName = requireText(fileName, "fileName");
        this.contentType = requireText(contentType, "contentType");
        this.size = Math.max(size, 0L);
        this.storagePath = requireText(storagePath, "storagePath");
    }

    public static StoredFile create(UUID tenantId, UUID organizationId, UUID uploadedByUserId, String fileName,
            String contentType, long size, String storagePath) {
        Instant now = Instant.now();
        return new StoredFile(UUID.randomUUID(), tenantId, now, now, organizationId, uploadedByUserId, fileName,
                contentType, size, storagePath);
    }

    public static StoredFile rehydrate(UUID id, UUID tenantId, Instant createdAt, Instant updatedAt,
            UUID organizationId, UUID uploadedByUserId, String fileName, String contentType, long size,
            String storagePath) {
        return new StoredFile(id, tenantId, createdAt, updatedAt, organizationId, uploadedByUserId, fileName,
                contentType, size, storagePath);
    }

    public UUID organizationId() { return organizationId; }
    public UUID uploadedByUserId() { return uploadedByUserId; }
    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public long size() { return size; }
    public String storagePath() { return storagePath; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
