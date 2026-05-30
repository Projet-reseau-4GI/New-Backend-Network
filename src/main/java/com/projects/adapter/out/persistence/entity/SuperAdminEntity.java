package com.projects.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC persistence entity for SuperAdmin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("super_admins")
public class SuperAdminEntity {

    @Id private Long id;
    @Column("name") private String name;
    @Column("email") private String email;
    @Column("password_hash") private String passwordHash;
    @Column("api_key") private String apiKey;
    @Column("email_verified") @Builder.Default private Boolean emailVerified = true;
    @Column("otp_code") private String otpCode;
    @Column("otp_expiry") private LocalDateTime otpExpiry;
    @Column("reset_code") private String resetCode;
    @Column("reset_code_expiry") private LocalDateTime resetCodeExpiry;
    @Column("reset_attempts") private Integer resetAttempts;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("updated_at") private LocalDateTime updatedAt;
}
