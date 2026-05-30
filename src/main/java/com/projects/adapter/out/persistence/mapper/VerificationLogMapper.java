package com.projects.adapter.out.persistence.mapper;

import com.projects.domain.model.VerificationLog;
import com.projects.adapter.out.persistence.entity.VerificationLogEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper: VerificationLog domain model ↔ VerificationLogEntity (R2DBC).
 */
@Component
public class VerificationLogMapper {

    public VerificationLog toDomain(VerificationLogEntity entity) {
        if (entity == null) return null;
        return VerificationLog.builder()
            .id(entity.getId())
            .platformId(entity.getPlatformId())
            .date(entity.getDate())
            .docType(entity.getDocType())
            .status(entity.getStatus())
            .reason(entity.getReason())
            .confidence(entity.getConfidence())
            .processingTimeMs(entity.getProcessingTimeMs())
            .documentNumber(entity.getDocumentNumber())
            .holderName(entity.getHolderName())
            .dateOfBirth(entity.getDateOfBirth())
            .issueDate(entity.getIssueDate())
            .expiryDate(entity.getExpiryDate())
            .additionalFields(entity.getAdditionalFields())
            .build();
    }

    public VerificationLogEntity toEntity(VerificationLog domain) {
        if (domain == null) return null;
        return VerificationLogEntity.builder()
            .id(domain.getId())
            .platformId(domain.getPlatformId())
            .date(domain.getDate())
            .docType(domain.getDocType())
            .status(domain.getStatus())
            .reason(domain.getReason())
            .confidence(domain.getConfidence())
            .processingTimeMs(domain.getProcessingTimeMs())
            .documentNumber(domain.getDocumentNumber())
            .holderName(domain.getHolderName())
            .dateOfBirth(domain.getDateOfBirth())
            .issueDate(domain.getIssueDate())
            .expiryDate(domain.getExpiryDate())
            .additionalFields(domain.getAdditionalFields())
            .build();
    }
}
