package com.projects.application.port.in.platform;

import com.projects.adapter.in.web.dto.*;
import reactor.core.publisher.Mono;

/**
 * Inbound port — Platform registration and email verification use cases.
 */
public interface RegisterPlatformUseCase {
    Mono<Void> register(RegisterRequest req);
    Mono<EmailVerificationResponse> verifyEmail(String email, String code);
    Mono<Void> resendOtp(String email);
}
