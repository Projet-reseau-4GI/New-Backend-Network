package com.yowyob.flashshop.controller;

import com.yowyob.flashshop.dto.AdminLoginRequestSuperAdmin;
import com.yowyob.flashshop.dto.AdminRegisterRequestSuperAdmin;
import com.yowyob.flashshop.dto.AdminVerifyOtpRequestSuperAdmin;
import com.yowyob.flashshop.service.AdminAuthServiceSuperAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminAuthControllerSuperAdmin {

    private final AdminAuthServiceSuperAdmin adminAuthServiceSuperAdmin;

    /**
     * Inscription d'un nouveau SuperAdmin.
     * Vérifie l'existence dans la base de données et envoie un OTP par email.
     */
    @PostMapping("/registerSuperAdmin")
    public Mono<ResponseEntity<String>> register(@RequestBody AdminRegisterRequestSuperAdmin req) {
        return adminAuthServiceSuperAdmin.register(req)
                .thenReturn(ResponseEntity
                        .ok("Inscription initiée. OTP envoyé à votre email. Veuillez vérifier votre compte."))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }

    /**
     * Vérification de l'OTP d'inscription → activation du compte et retour du JWT.
     */
    @PostMapping("/verify-registrationSuperAdmin")
    public Mono<ResponseEntity<Object>> verifyRegistration(@RequestBody AdminVerifyOtpRequestSuperAdmin req) {
        return adminAuthServiceSuperAdmin.verifyRegistration(req)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }

    /**
     * Connexion SuperAdmin — vérification directe des coordonnées (email + mot de
     * passe).
     * Retourne un JWT immédiatement sans OTP.
     */
    @PostMapping("/loginSuperAdmin")
    public Mono<ResponseEntity<Object>> login(@RequestBody AdminLoginRequestSuperAdmin req) {
        return adminAuthServiceSuperAdmin.login(req)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(e.getMessage())));
    }
}
