package com.yowyob.flashshop.model;

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
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("platforms")
public class Platform {

    /**
     * Unique identifier for the platform.
     */
    @Id
    private Long id;

    /**
     * Name of the platform.
     */
    @Column("name")
    private String name;

    /**
     * Email associated with the platform.
     */
    @Column("email")
    private String email;

    /**
     * BCrypt-hashed password for portal login.
     */
    @Column("password_hash")
    private String password_hash;

    /**
     * API key for platform identification in requests.
     */
    @Column("api_key")
    private String api_key;

    /**
     * OTP code sent by email for email verification or API key regeneration.
     */
    @Column("otp_code")
    private String otp_code;

    /**
     * Expiration date and time for the OTP code.
     */
    @Column("otp_expiry")
    private LocalDateTime otp_expiry;

    /**
     * Whether the platform's email has been verified.
     */
    @Column("email_verified")
    private Boolean email_verified;

    /**
     * Code for password reset flow.
     */
    @Column("reset_code")
    private String reset_code;

    /**
     * Expiration date and time for the reset code.
     */
    @Column("reset_code_expiry")
    private LocalDateTime reset_code_expiry;

    /**
     * Number of attempts for password reset.
     */
    @Column("reset_attempts")
    private Integer reset_attempts;

    /**
     * Whether the platform is active.
     */
    @Column("active")
    private Boolean active;

    /**
     * Creation date and time of the platform record.
     */
    @Column("created_at")
    private LocalDateTime created_at;

    /**
     * Last update date and time of the platform record.
     */
    @Column("updated_at")
    private LocalDateTime updated_at;

    // Getters for compatibility with camelCase naming convention for methods
    public String getPasswordHash() {
        return password_hash;
    }

    public String getApiKey() {
        return api_key;
    }

    public String getOtpCode() {
        return otp_code;
    }

    public LocalDateTime getOtpExpiry() {
        return otp_expiry;
    }

    public Boolean getEmailVerified() {
        return email_verified;
    }

    public String getResetCode() {
        return reset_code;
    }

    public LocalDateTime getResetCodeExpiry() {
        return reset_code_expiry;
    }

    public Integer getResetAttempts() {
        return reset_attempts;
    }

    public LocalDateTime getCreatedAt() {
        return created_at;
    }

    public LocalDateTime getUpdatedAt() {
        return updated_at;
    }

    public void setPasswordHash(String password_hash) {
        this.password_hash = password_hash;
    }

    public void setApiKey(String api_key) {
        this.api_key = api_key;
    }

    public void setOtpCode(String otp_code) {
        this.otp_code = otp_code;
    }

    public void setOtpExpiry(LocalDateTime otp_expiry) {
        this.otp_expiry = otp_expiry;
    }

    public void setEmailVerified(Boolean email_verified) {
        this.email_verified = email_verified;
    }

    public void setResetCode(String reset_code) {
        this.reset_code = reset_code;
    }

    public void setResetCodeExpiry(LocalDateTime reset_code_expiry) {
        this.reset_code_expiry = reset_code_expiry;
    }

    public void setResetAttempts(Integer reset_attempts) {
        this.reset_attempts = reset_attempts;
    }

    public void setCreatedAt(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    public void setUpdatedAt(LocalDateTime updated_at) {
        this.updated_at = updated_at;
    }
}
