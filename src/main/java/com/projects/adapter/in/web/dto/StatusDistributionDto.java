package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Status distribution slice (ACCEPTED / REJECTED) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusDistributionDto {
    private String status;
    private Long count;
    private Double percentage;
}
