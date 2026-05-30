package com.projects.application.port.in.platform;

import com.projects.adapter.in.web.dto.*;
import reactor.core.publisher.Mono;

/**
 * Inbound port — Platform profile, password, and API key management use cases.
 */
public interface ManagePlatformUseCase {
    Mono<PlatformResponse> getProfile(Long platformId);
    Mono<Void> changePassword(Long platformId, ChangePasswordRequest req);
    Mono<Void> forgotPassword(String email);
    Mono<Void> resetPassword(ResetPasswordRequest req);
    Mono<Void> requestApiKeyRegeneration(Long platformId);
    Mono<EmailVerificationResponse> confirmApiKeyRegeneration(Long platformId, String code);
}
