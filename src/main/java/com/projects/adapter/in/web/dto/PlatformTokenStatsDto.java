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
public class PlatformTokenStatsDto {
    private Long platformId;
    private String platformName;
    private String email;
    private String apiKey;
    private Boolean active;
    private Long totalCalls;
    private LocalDateTime lastCallDate;
}
