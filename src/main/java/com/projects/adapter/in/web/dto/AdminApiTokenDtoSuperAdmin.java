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
public class AdminApiTokenDtoSuperAdmin {
    private String platformName;
    private String apiKey;
    private boolean active;
    private Long totalCalls;
    private LocalDateTime lastCallDate;
}
