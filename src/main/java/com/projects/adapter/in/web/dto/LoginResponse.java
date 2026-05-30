package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /** JWT session token for the admin portal */
    private String token;
    private Long platformId;
    private String name;
    private String email;
    private Boolean emailVerified;
    private Boolean active;
}
