package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.AdminLoginRequestSuperAdmin;
import com.projects.adapter.in.web.dto.AdminRegisterRequestSuperAdmin;
import com.projects.adapter.in.web.dto.AdminVerifyOtpRequestSuperAdmin;
import com.projects.application.port.in.admin.AdminAuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Inbound adapter (Web) — SuperAdmin authentication.
 * Injects AdminAuthUseCase port — no direct service coupling.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "SuperAdmin — Auth", description = "Inscription, vérification OTP et connexion SuperAdmin")
public class AdminAuthControllerSuperAdmin {

    private final AdminAuthUseCase adminAuthUseCase;

    @PostMapping("/registerSuperAdmin")
    @Operation(summary = "Inscription d'un nouveau SuperAdmin")
    public Mono<ResponseEntity<String>> register(@RequestBody AdminRegisterRequestSuperAdmin req) {
        return adminAuthUseCase.register(req)
            .thenReturn(ResponseEntity.ok("Inscription initiée. OTP envoyé à votre email."))
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }

    @PostMapping("/verify-registrationSuperAdmin")
    @Operation(summary = "Vérification OTP d'inscription SuperAdmin")
    public Mono<ResponseEntity<Object>> verifyRegistration(@RequestBody AdminVerifyOtpRequestSuperAdmin req) {
        return adminAuthUseCase.verifyRegistration(req)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }

    @PostMapping("/loginSuperAdmin")
    @Operation(summary = "Connexion SuperAdmin")
    public Mono<ResponseEntity<Object>> login(@RequestBody AdminLoginRequestSuperAdmin req) {
        return adminAuthUseCase.login(req)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }
}
