package yowyob.comops.api.file.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

public abstract class PersistableEntity {
    public abstract UUID getId();
    public abstract UUID getTenantId();
    public abstract Instant getCreatedAt();
    public abstract Instant getUpdatedAt();
}