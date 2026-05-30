package com.projects.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminVerifyOtpRequestSuperAdmin {
    private String email;
    private String otpCode;
}
