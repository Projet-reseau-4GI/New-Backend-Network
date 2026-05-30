package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Details of a single recent verification for the last-10 table */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentVerificationDto {
    private Long id;
    private Long platformId;
    private String platformName;
    private LocalDateTime date;
    private String docType;
    private String status;
    private String reason;
    private Double confidence;
    private Integer processingTimeMs;
}
