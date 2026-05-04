package yowyob.comops.api.common.domain.model;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseEntity {
    private final UUID id;
    private final UUID tenantId;
    private final Instant createdAt;
    private final Instant updatedAt;

    protected BaseEntity(UUID id, UUID tenantId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
