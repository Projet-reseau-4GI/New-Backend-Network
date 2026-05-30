package com.projects.dto;

import lombok.Data;

@Data
public class OtpVerification {
    private String email;
    private String code;
}
