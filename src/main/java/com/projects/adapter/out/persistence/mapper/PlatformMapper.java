package com.projects.adapter.out.persistence.mapper;

import com.projects.domain.model.Platform;
import com.projects.adapter.out.persistence.entity.PlatformEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper: Platform domain model ↔ PlatformEntity (R2DBC).
 */
@Component
public class PlatformMapper {

    public Platform toDomain(PlatformEntity entity) {
        if (entity == null) return null;
        return Platform.builder()
            .id(entity.getId())
            .name(entity.getName())
            .email(entity.getEmail())
            .passwordHash(entity.getPasswordHash())
            .apiKey(entity.getApiKey())
            .otpCode(entity.getOtpCode())
            .otpExpiry(entity.getOtpExpiry())
            .emailVerified(entity.getEmailVerified())
            .resetCode(entity.getResetCode())
            .resetCodeExpiry(entity.getResetCodeExpiry())
            .resetAttempts(entity.getResetAttempts())
            .active(entity.getActive())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    public PlatformEntity toEntity(Platform domain) {
        if (domain == null) return null;
        return PlatformEntity.builder()
            .id(domain.getId())
            .name(domain.getName())
            .email(domain.getEmail())
            .passwordHash(domain.getPasswordHash())
            .apiKey(domain.getApiKey())
            .otpCode(domain.getOtpCode())
            .otpExpiry(domain.getOtpExpiry())
            .emailVerified(domain.getEmailVerified())
            .resetCode(domain.getResetCode())
            .resetCodeExpiry(domain.getResetCodeExpiry())
            .resetAttempts(domain.getResetAttempts())
            .active(domain.getActive())
            .createdAt(domain.getCreatedAt())
            .updatedAt(domain.getUpdatedAt())
            .build();
    }
}
