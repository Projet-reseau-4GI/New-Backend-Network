package com.projects.domain.model;

import java.time.LocalDateTime;

/**
 * Pure domain entity representing a Tenant/Platform.
 * No framework annotations — belongs entirely to the domain.
 */
public class Platform {

    private Long id;
    private String name;
    private String email;
    private String passwordHash;
    private String apiKey;
    private String otpCode;
    private LocalDateTime otpExpiry;
    private Boolean emailVerified;
    private String resetCode;
    private LocalDateTime resetCodeExpiry;
    private Integer resetAttempts;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Platform() {}

    private Platform(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.email = b.email;
        this.passwordHash = b.passwordHash;
        this.apiKey = b.apiKey;
        this.otpCode = b.otpCode;
        this.otpExpiry = b.otpExpiry;
        this.emailVerified = b.emailVerified;
        this.resetCode = b.resetCode;
        this.resetCodeExpiry = b.resetCodeExpiry;
        this.resetAttempts = b.resetAttempts;
        this.active = b.active;
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
        private String otpCode;
        private LocalDateTime otpExpiry;
        private Boolean emailVerified;
        private String resetCode;
        private LocalDateTime resetCodeExpiry;
        private Integer resetAttempts;
        private Boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder passwordHash(String passwordHash) { this.passwordHash = passwordHash; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder otpCode(String otpCode) { this.otpCode = otpCode; return this; }
        public Builder otpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; return this; }
        public Builder emailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public Builder resetCode(String resetCode) { this.resetCode = resetCode; return this; }
        public Builder resetCodeExpiry(LocalDateTime resetCodeExpiry) { this.resetCodeExpiry = resetCodeExpiry; return this; }
        public Builder resetAttempts(Integer resetAttempts) { this.resetAttempts = resetAttempts; return this; }
        public Builder active(Boolean active) { this.active = active; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public Platform build() { return new Platform(this); }
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getApiKey() { return apiKey; }
    public String getOtpCode() { return otpCode; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public Boolean getEmailVerified() { return emailVerified; }
    public String getResetCode() { return resetCode; }
    public LocalDateTime getResetCodeExpiry() { return resetCodeExpiry; }
    public Integer getResetAttempts() { return resetAttempts; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
    public void setResetCode(String resetCode) { this.resetCode = resetCode; }
    public void setResetCodeExpiry(LocalDateTime resetCodeExpiry) { this.resetCodeExpiry = resetCodeExpiry; }
    public void setResetAttempts(Integer resetAttempts) { this.resetAttempts = resetAttempts; }
    public void setActive(Boolean active) { this.active = active; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
