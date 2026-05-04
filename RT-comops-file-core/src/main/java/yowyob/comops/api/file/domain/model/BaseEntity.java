package yowyob.comops.api.file.domain.model;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseEntity {
    public abstract UUID id();
    public abstract Instant createdAt();
    public abstract Instant updatedAt();
}