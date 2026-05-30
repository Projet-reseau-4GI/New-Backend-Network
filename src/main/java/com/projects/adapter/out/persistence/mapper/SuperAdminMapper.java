package com.projects.adapter.out.persistence.mapper;

import com.projects.domain.model.SuperAdmin;
import com.projects.adapter.out.persistence.entity.SuperAdminEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper: SuperAdmin domain model ↔ SuperAdminEntity (R2DBC).
 */
@Component
public class SuperAdminMapper {

    public SuperAdmin toDomain(SuperAdminEntity entity) {
        if (entity == null) return null;
        return SuperAdmin.builder()
            .id(entity.getId())
            .name(entity.getName())
            .email(entity.getEmail())
            .passwordHash(entity.getPasswordHash())
            .apiKey(entity.getApiKey())
            .emailVerified(entity.getEmailVerified())
            .otpCode(entity.getOtpCode())
            .otpExpiry(entity.getOtpExpiry())
            .resetCode(entity.getResetCode())
            .resetCodeExpiry(entity.getResetCodeExpiry())
            .resetAttempts(entity.getResetAttempts())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    public SuperAdminEntity toEntity(SuperAdmin domain) {
        if (domain == null) return null;
        return SuperAdminEntity.builder()
            .id(domain.getId())
            .name(domain.getName())
            .email(domain.getEmail())
            .passwordHash(domain.getPasswordHash())
            .apiKey(domain.getApiKey())
            .emailVerified(domain.getEmailVerified())
            .otpCode(domain.getOtpCode())
            .otpExpiry(domain.getOtpExpiry())
            .resetCode(domain.getResetCode())
            .resetCodeExpiry(domain.getResetCodeExpiry())
            .resetAttempts(domain.getResetAttempts())
            .createdAt(domain.getCreatedAt())
            .updatedAt(domain.getUpdatedAt())
            .build();
    }
}
