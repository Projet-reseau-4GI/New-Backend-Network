package com.projects.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity representing a Tenant/Platform in the system.
 * Used for multi-tenancy and API key authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("platforms")
public class Platform {

    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    /** BCrypt-hashed password for portal login */
    @Column("password_hash")
    private String passwordHash;

    @Column("api_key")
    private String apiKey;

    /** OTP code sent by email for email verification or API key regeneration */
    @Column("otp_code")
    private String otpCode;

    @Column("otp_expiry")
    private LocalDateTime otpExpiry;

    /** Whether the platform's email has been verified */
    @Column("email_verified")
    private Boolean emailVerified;

    /** Code for password reset flow */
    @Column("reset_code")
    private String resetCode;

    @Column("reset_code_expiry")
    private LocalDateTime resetCodeExpiry;

    @Column("reset_attempts")
    private Integer resetAttempts;

    @Column("active")
    private Boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
