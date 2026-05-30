package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single data point on a time-series chart. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {
    /** Label: date string (day/week/month) */
    private String label;
    private Long count;
    private Long successCount;
    private Long failureCount;
}
