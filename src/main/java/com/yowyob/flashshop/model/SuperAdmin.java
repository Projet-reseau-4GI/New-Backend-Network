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
 * Entity representing a Super Administrator.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("super_admins")
public class SuperAdmin {

    /**
     * Unique identifier for the super admin.
     */
    @Id
    private Long id;

    /**
     * Name of the super admin.
     */
    @Column("name")
    private String name;

    /**
     * Email of the super admin.
     */
    @Column("email")
    private String email;

    /**
     * BCrypt-hashed password for login.
     */
    @Column("password_hash")
    private String password_hash;

    /**
     * API key for super admin actions.
     */
    @Column("api_key")
    private String api_key;

    /**
     * Whether the email has been verified.
     */
    @Column("email_verified")
    @Builder.Default
    private Boolean email_verified = true;

    /**
     * OTP code sent by email.
     */
    @Column("otp_code")
    private String otp_code;

    /**
     * Expiration date and time for the OTP code.
     */
    @Column("otp_expiry")
    private LocalDateTime otp_expiry;

    /**
     * Code for password reset.
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
     * Creation date and time of the super admin record.
     */
    @Column("created_at")
    private LocalDateTime created_at;

    /**
     * Last update date and time of the super admin record.
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

    public Boolean getEmailVerified() {
        return email_verified;
    }

    public String getOtpCode() {
        return otp_code;
    }

    public LocalDateTime getOtpExpiry() {
        return otp_expiry;
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

    public void setEmailVerified(Boolean email_verified) {
        this.email_verified = email_verified;
    }

    public void setOtpCode(String otp_code) {
        this.otp_code = otp_code;
    }

    public void setOtpExpiry(LocalDateTime otp_expiry) {
        this.otp_expiry = otp_expiry;
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
