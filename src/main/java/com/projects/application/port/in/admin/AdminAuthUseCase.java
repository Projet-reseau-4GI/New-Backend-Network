package com.projects.application.port.in.admin;

import com.projects.adapter.in.web.dto.*;
import reactor.core.publisher.Mono;

/**
 * Inbound port — SuperAdmin authentication use cases.
 */
public interface AdminAuthUseCase {
    Mono<Void> register(AdminRegisterRequestSuperAdmin req);
    Mono<LoginResponse> verifyRegistration(AdminVerifyOtpRequestSuperAdmin req);
    Mono<LoginResponse> login(AdminLoginRequestSuperAdmin req);
}
