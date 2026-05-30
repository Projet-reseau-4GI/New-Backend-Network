package com.projects.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port — email sending contract.
 * The domain depends on this interface; infrastructure (Brevo) implements it.
 */
public interface EmailServicePort {
    Mono<Void> sendOtp(String to, String code, String platformName);
    Mono<Void> sendPasswordReset(String to, String code, String platformName);
    Mono<Void> sendPasswordChangedNotification(String to, String platformName);
    Mono<Void> sendApiKeyRegeneratedNotification(String to, String platformName);
}
