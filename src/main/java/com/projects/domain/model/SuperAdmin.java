package com.projects.domain.model;

import java.time.LocalDateTime;

/**
 * Pure domain entity for a SuperAdmin.
 * No framework annotations.
 */
public class SuperAdmin {

    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String apiKey;
    private Boolean emailVerified;
    private String otpCode;
    private LocalDateTime otpExpiry;
    private String resetCode;
    private LocalDateTime resetCodeExpiry;
    private Integer resetAttempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SuperAdmin() {}

    private SuperAdmin(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.apiKey = b.apiKey;
        this.emailVerified = b.emailVerified != null ? b.emailVerified : true;
        this.otpCode = b.otpCode;
        this.otpExpiry = b.otpExpiry;
        this.resetCode = b.resetCode;
        this.resetCodeExpiry = b.resetCodeExpiry;
        this.resetAttempts = b.resetAttempts;
        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String name;
        private String email;
        private String passwordHash;
        private String apiKey;
        private Boolean emailVerified = true;
        private String otpCode;
        private LocalDateTime otpExpiry;
        private String resetCode;
        private LocalDateTime resetCodeExpiry;
        private Integer resetAttempts;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder emailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public Builder otpCode(String otpCode) { this.otpCode = otpCode; return this; }
        public Builder otpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; return this; }
        public Builder resetCode(String resetCode) { this.resetCode = resetCode; return this; }
        public Builder resetCodeExpiry(LocalDateTime resetCodeExpiry) { this.resetCodeExpiry = resetCodeExpiry; return this; }
        public Builder resetAttempts(Integer resetAttempts) { this.resetAttempts = resetAttempts; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public SuperAdmin build() { return new SuperAdmin(this); }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getApiKey() { return apiKey; }
    public Boolean getEmailVerified() { return emailVerified; }
    public String getOtpCode() { return otpCode; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public String getResetCode() { return resetCode; }
    public LocalDateTime getResetCodeExpiry() { return resetCodeExpiry; }
    public Integer getResetAttempts() { return resetAttempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }
    public void setResetCode(String resetCode) { this.resetCode = resetCode; }
    public void setResetCodeExpiry(LocalDateTime resetCodeExpiry) { this.resetCodeExpiry = resetCodeExpiry; }
    public void setResetAttempts(Integer resetAttempts) { this.resetAttempts = resetAttempts; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
