package com.projects.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRegisterRequestSuperAdmin {
    private String name;
    private String email;
    private String password;
}
