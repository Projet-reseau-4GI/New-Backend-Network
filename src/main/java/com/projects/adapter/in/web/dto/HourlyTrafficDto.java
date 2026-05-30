package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Hourly traffic bucket (0–23) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HourlyTrafficDto {
    private Integer hour;
    private Long count;
}
