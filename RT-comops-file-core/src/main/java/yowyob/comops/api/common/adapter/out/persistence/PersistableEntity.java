package yowyob.comops.api.common.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

public interface PersistableEntity {
    UUID id();
    UUID tenantId();
    Instant createdAt();
    Instant updatedAt();
}
