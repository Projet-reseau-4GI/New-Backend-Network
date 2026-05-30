package com.projects.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PlatformResponse {
    private Long id;
    private String name;
    private String email;
    private String apiKey; // Raw API key, only populated on creation/regeneration
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
