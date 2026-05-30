package com.yowyob.flashshop.service;

import reactor.core.publisher.Mono;

/**
 * Email service interface for all outgoing emails in VerifID.
 */
public interface EmailService {

    /** Send OTP code for email verification or API key regeneration request */
    Mono<Void> sendOtp(String to, String code, String platformName);

    /** Send password reset code */
    Mono<Void> sendPasswordReset(String to, String code, String platformName);

    /** Notify that password has been changed successfully */
    Mono<Void> sendPasswordChangedNotification(String to, String platformName);

    /** Notify that API key has been regenerated — user should check portal */
    Mono<Void> sendApiKeyRegeneratedNotification(String to, String platformName);
}
