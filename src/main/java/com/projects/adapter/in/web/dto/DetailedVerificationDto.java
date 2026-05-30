package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedVerificationDto {
    private Long id;
    private Long platformId;
    private String platformName;
    private LocalDateTime date;
    private String docType;
    private String status;
    private Double confidence;
    private Integer processingTimeMs;

    private String documentNumber;
    private String holderName;
    private String dateOfBirth;
    private String issueDate;
    private String expiryDate;
    private String additionalFields;
}
