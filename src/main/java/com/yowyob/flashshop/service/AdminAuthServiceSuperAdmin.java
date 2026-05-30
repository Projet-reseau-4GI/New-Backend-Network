package com.yowyob.flashshop.service;

import com.yowyob.flashshop.dto.AdminLoginRequestSuperAdmin;
import com.yowyob.flashshop.dto.AdminRegisterRequestSuperAdmin;
import com.yowyob.flashshop.dto.AdminVerifyOtpRequestSuperAdmin;
import com.yowyob.flashshop.dto.LoginResponse;
import com.yowyob.flashshop.model.SuperAdmin;
import com.yowyob.flashshop.repository.SuperAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Authentication service for SuperAdmin accounts.
 * Provides registration, OTP verification, and login functionalities.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthServiceSuperAdmin {

        private final SuperAdminRepository super_admin_repository;
        private final EmailService email_service;
        private final JwtService jwt_service;
        private final BCryptPasswordEncoder password_encoder;
        private final Random random = new Random();

        // ─────────────────────────────────────────────────────────────────────────
        // REGISTRATION
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Creates a SuperAdmin account and sends an OTP for verification.
         * First checks that the email does not already exist in the database.
         *
         * @param req The registration request containing super admin details.
         * @return A Mono that completes when the registration email is sent.
         */
        public Mono<Void> register(AdminRegisterRequestSuperAdmin req) {
                log.info("SuperAdmin registration request for email: {}", req.getEmail());

                return super_admin_repository.findByEmail(req.getEmail())
                                .flatMap(existing -> Mono
                                                .<Void>error(new RuntimeException(
                                                                "A SuperAdmin account with this email already exists.")))
                                .switchIfEmpty(
                                                Mono.defer(() -> {
                                                        String hashed_password = password_encoder
                                                                        .encode(req.getPassword());
                                                        String otp = generateOtp();

                                                        SuperAdmin new_admin = SuperAdmin.builder()
                                                                        .name(req.getName())
                                                                        .email(req.getEmail())
                                                                        .password_hash(hashed_password)
                                                                        .email_verified(false)
                                                                        .otp_code(otp)
                                                                        .otp_expiry(LocalDateTime.now().plusMinutes(10))
                                                                        .created_at(LocalDateTime.now())
                                                                        .updated_at(LocalDateTime.now())
                                                                        .build();

                                                        return super_admin_repository.save(new_admin)
                                                                        .then(email_service.sendOtp(req.getEmail(), otp,
                                                                                        "SuperAdmin"));
                                                }))
                                .then();
        }

        /**
         * Verifies the registration OTP and activates the SuperAdmin account.
         *
         * @param req The OTP verification request.
         * @return A Mono containing the login response with a JWT token.
         */
        public Mono<LoginResponse> verifyRegistration(AdminVerifyOtpRequestSuperAdmin req) {
                log.info("SuperAdmin OTP verification for email: {}", req.getEmail());

                return super_admin_repository.findByEmail(req.getEmail())
                                .switchIfEmpty(Mono.error(new RuntimeException("SuperAdmin account not found.")))
                                .flatMap(admin -> {
                                        if (admin.getOtp_code() == null
                                                        || !admin.getOtp_code().equals(req.getOtpCode())) {
                                                return Mono.error(new RuntimeException("Invalid OTP code."));
                                        }
                                        if (admin.getOtp_expiry() == null
                                                        || admin.getOtp_expiry().isBefore(LocalDateTime.now())) {
                                                return Mono.error(new RuntimeException("OTP code expired."));
                                        }

                                        admin.setOtp_code(null);
                                        admin.setOtp_expiry(null);
                                        admin.setEmail_verified(true);
                                        admin.setUpdated_at(LocalDateTime.now());

                                        String token = jwt_service.generateToken(
                                                        admin.getEmail(), admin.getId(),
                                                        admin.getName() != null ? admin.getName() : "SuperAdmin",
                                                        "ROLE_SUPERADMIN");

                                        return super_admin_repository.save(admin)
                                                        .thenReturn(LoginResponse.builder()
                                                                        .token(token)
                                                                        .email(admin.getEmail())
                                                                        .name(admin.getName() != null ? admin.getName()
                                                                                        : "SuperAdmin")
                                                                        .emailVerified(true)
                                                                        .active(true)
                                                                        .build());
                                });
        }

        // ─────────────────────────────────────────────────────────────────────────
        // LOGIN — Direct verification
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Simply verifies that coordinates (email + password) exist in the super_admins
         * table,
         * then returns a JWT immediately. No OTP is sent during login.
         *
         * @param req The login request containing email and password.
         * @return A Mono containing the login response with a JWT token.
         */
        public Mono<LoginResponse> login(AdminLoginRequestSuperAdmin req) {
                log.info("SuperAdmin login attempt for email: {}", req.getEmail());

                return super_admin_repository.findByEmail(req.getEmail())
                                .switchIfEmpty(Mono.error(new RuntimeException("SuperAdmin account not found.")))
                                .flatMap(admin -> {
                                        if (!admin.getEmail_verified()) {
                                                return Mono.error(new RuntimeException(
                                                                "Account not verified. Please complete registration via OTP."));
                                        }
                                        if (!password_encoder.matches(req.getPassword(), admin.getPassword_hash())) {
                                                return Mono.error(new RuntimeException("Incorrect credentials."));
                                        }

                                        String token = jwt_service.generateToken(
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

        // ─────────────────────────────────────────────────────────────────────────
        // UTILS
        // ─────────────────────────────────────────────────────────────────────────

        private String generateOtp() {
                return String.format("%06d", random.nextInt(1_000_000));
        }
}
