package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for SuperAdmin verification results.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVerificationResultDtoSuperAdmin {
    private String id;
    private LocalDateTime date;
    private String doc_type;
    private String status;
    private Integer processing_time_ms;
    private String document_number;
    private String holder_name;
    private String date_of_birth;
    private String issue_date;
    private String expiry_date;
    private Double confidence_score;
    private String additional_fields;
}
