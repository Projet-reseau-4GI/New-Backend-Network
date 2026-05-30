package com.projects.application.port.in.platform;

import com.projects.adapter.in.web.dto.LoginRequest;
import com.projects.adapter.in.web.dto.LoginResponse;
import reactor.core.publisher.Mono;

/**
 * Inbound port — Platform authentication use case.
 */
public interface AuthenticatePlatformUseCase {
    Mono<LoginResponse> login(LoginRequest req);
}
