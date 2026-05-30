package com.projects.application.service.admin;

import com.projects.domain.model.SuperAdmin;
import com.projects.application.port.in.admin.AdminAuthUseCase;
import com.projects.application.port.out.EmailServicePort;
import com.projects.application.port.out.SuperAdminRepositoryPort;
import com.projects.application.port.out.TokenServicePort;
import com.projects.adapter.in.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Application use case — SuperAdmin authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthUseCaseImpl implements AdminAuthUseCase {

    private final SuperAdminRepositoryPort superAdminRepository;
    private final EmailServicePort emailService;
    private final TokenServicePort tokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Random random = new Random();

    @Override
    public Mono<Void> register(AdminRegisterRequestSuperAdmin req) {
        log.info("SuperAdmin registration request for email: {}", req.getEmail());
        return superAdminRepository.findByEmail(req.getEmail())
            .flatMap(existing -> Mono.<Void>error(
                new RuntimeException("A SuperAdmin account with this email already exists.")))
            .switchIfEmpty(Mono.defer(() -> {
                String hashedPassword = passwordEncoder.encode(req.getPassword());
                String otp = generateOtp();

                SuperAdmin newAdmin = SuperAdmin.builder()
                    .name(req.getName())
                    .email(req.getEmail())
                    .passwordHash(hashedPassword)
                    .emailVerified(false)
                    .otpCode(otp)
                    .otpExpiry(LocalDateTime.now().plusMinutes(10))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

                return superAdminRepository.save(newAdmin)
                    .then(emailService.sendOtp(req.getEmail(), otp, "SuperAdmin"));
            }))
            .then();
    }

    @Override
    public Mono<LoginResponse> verifyRegistration(AdminVerifyOtpRequestSuperAdmin req) {
        log.info("SuperAdmin OTP verification for email: {}", req.getEmail());
        return superAdminRepository.findByEmail(req.getEmail())
            .switchIfEmpty(Mono.error(new RuntimeException("SuperAdmin account not found.")))
            .flatMap(admin -> {
                if (admin.getOtpCode() == null || !admin.getOtpCode().equals(req.getOtpCode())) {
                    return Mono.error(new RuntimeException("Invalid OTP code."));
                }
                if (admin.getOtpExpiry() == null || admin.getOtpExpiry().isBefore(LocalDateTime.now())) {
                    return Mono.error(new RuntimeException("OTP code expired."));
                }

                admin.setOtpCode(null);
                admin.setOtpExpiry(null);
                admin.setEmailVerified(true);
                admin.setUpdatedAt(LocalDateTime.now());

                String token = tokenService.generateToken(
                    admin.getEmail(), admin.getId(),
                    admin.getName() != null ? admin.getName() : "SuperAdmin",
                    "ROLE_SUPERADMIN");

                return superAdminRepository.save(admin)
                    .thenReturn(LoginResponse.builder()
                        .token(token)
                        .email(admin.getEmail())
                        .name(admin.getName() != null ? admin.getName() : "SuperAdmin")
                        .emailVerified(true)
                        .active(true)
                        .build());
            });
    }

    @Override
    public Mono<LoginResponse> login(AdminLoginRequestSuperAdmin req) {
        log.info("SuperAdmin login attempt for email: {}", req.getEmail());
        return superAdminRepository.findByEmail(req.getEmail())
            .switchIfEmpty(Mono.error(new RuntimeException("SuperAdmin account not found.")))
            .flatMap(admin -> {
                if (!admin.getEmailVerified()) {
                    return Mono.error(new RuntimeException("Account not verified. Please complete registration via OTP."));
                }
                if (!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash())) {
                    return Mono.error(new RuntimeException("Incorrect credentials."));
                }

                String token = tokenService.generateToken(
                    admin.getEmail(), admin.getId(),
                    admin.getName() != null ? admin.getName() : "SuperAdmin",
                    "ROLE_SUPERADMIN");

                return Mono.just(LoginResponse.builder()
                    .token(token)
                    .email(admin.getEmail())
                    .name(admin.getName() != null ? admin.getName() : "SuperAdmin")
                    .emailVerified(true)
                    .active(true)
                    .build());
            });
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
