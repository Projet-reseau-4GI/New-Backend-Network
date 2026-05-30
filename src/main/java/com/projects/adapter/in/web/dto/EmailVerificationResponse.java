package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned when an email is successfully verified.
 * The raw API key is shown to the user ONCE and must be stored client-side.
 * The backend only stores the hashed version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationResponse {
    private String message;
    /** Raw API key to display to the user — only returned once, never emailed */
    private String apiKey;
    private Long platformId;
    private String name;
    private String email;
}
