package com.yowyob.flashshop.service;

import com.yowyob.flashshop.dto.*;
import com.yowyob.flashshop.model.Platform;
import com.yowyob.flashshop.repository.PlatformRepository;
import com.yowyob.flashshop.repository.SuperAdminRepository;
import com.yowyob.flashshop.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Complete authentication service for the VerifID platform portal.
 *
 * Flow summary:
 * 1. register() → create platform (inactive), send OTP by email
 * 2. verifyEmail() → validate OTP, activate platform, return raw API key to
 * frontend
 * 3. login() → check email+password, return JWT session token
 * 4. regenerateApiKey() → (JWT protected) send OTP by email first
 * 5. confirmRegenerateApiKey() → validate OTP, generate new API key, return it
 * to frontend
 * 6. forgotPassword() → send reset code by email
 * 7. resetPassword() → validate code, save new hashed password
 * 8. changePassword() → (JWT protected) validate old password, save new hash
 * 9. getProfile() → (JWT protected) return platform info
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAuthService {

    private final PlatformRepository platform_repository;
    private final SuperAdminRepository super_admin_repository;
    private final EmailService email_service;
    private final JwtService jwt_service;
    private final BCryptPasswordEncoder password_encoder;
    private final Random random = new Random();

    // ─────────────────────────────────────────────────────────────────────────
    // 1. REGISTER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new platform.
     * - Checks that email is not already taken.
     * - Hashes the password.
     * - Generates a placeholder API key (will be finalised after OTP verification).
     * - Sends OTP by email for account activation.
     * - Account is inactive (emailVerified=false) until OTP is validated.
     *
     * @param req The registration request containing platform details.
     * @return A Mono that completes when the registration email is sent.
     */
    public Mono<Void> register(RegisterRequest req) {
        return platform_repository.findByEmail(req.getEmail())
                .flatMap(existing -> Mono.<Void>error(
                        new IllegalArgumentException("A platform with this email already exists.")))
                .switchIfEmpty(Mono.defer(() -> {
                    String otp = generateOtp();
                    // Temporary placeholder API key — will be replaced after email verification
                    String raw_key = UUID.randomUUID().toString();
                    String hashed_key = SecurityUtils.hashApiKey(raw_key);

                    Platform platform = Platform.builder()
                            .name(req.getName())
                            .email(req.getEmail())
                            .password_hash(password_encoder.encode(req.getPassword()))
                            .api_key(hashed_key)
                            .otp_code(otp)
                            .otp_expiry(LocalDateTime.now().plusMinutes(15))
                            .email_verified(false)
                            .reset_attempts(0)
                            .active(false)
                            .created_at(LocalDateTime.now())
                            .updated_at(LocalDateTime.now())
                            .build();

                    return platform_repository.save(platform)
                            .then(email_service.sendOtp(req.getEmail(), otp, req.getName()));
                }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VERIFY EMAIL (OTP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the OTP sent during registration.
     * On success:
     * - Marks email as verified, activates the platform.
     * - Generates a fresh API key.
     * - Returns the raw API key to the frontend (displayed once, never re-sent).
     *
     * @param email The email address to verify.
     * @param code  The OTP code received by the user.
     * @return A Mono containing the email verification response.
     */
    public Mono<EmailVerificationResponse> verifyEmail(String email, String code) {
        return platform_repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("Account not found.")))
                .flatMap(platform -> {
                    if (platform.getEmail_verified() != null && platform.getEmail_verified()) {
                        return Mono.error(new IllegalStateException("This account is already verified."));
                    }
                    if (platform.getOtp_code() == null || !platform.getOtp_code().equals(code)) {
                        return Mono.error(new RuntimeException("Invalid OTP code."));
                    }
                    if (platform.getOtp_expiry() == null || platform.getOtp_expiry().isBefore(LocalDateTime.now())) {
                        return Mono.error(new RuntimeException("OTP code expired. Please request a new one."));
                    }

                    // Generate the definitive API key
                    String raw_key = UUID.randomUUID().toString();
                    String hashed_key = SecurityUtils.hashApiKey(raw_key);

                    platform.setApi_key(hashed_key);
                    platform.setOtp_code(null);
                    platform.setOtp_expiry(null);
                    platform.setEmail_verified(true);
                    platform.setActive(true);
                    platform.setUpdated_at(LocalDateTime.now());

                    return platform_repository.save(platform)
                            .map(saved -> EmailVerificationResponse.builder()
                                    .message(
                                            "Account activated successfully. Keep your API key safe, it will not be shown again.")
                                    .apiKey(raw_key)
                                    .platformId(saved.getId())
                                    .name(saved.getName())
                                    .email(saved.getEmail())
                                    .build());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a platform with email + password.
     * Returns a JWT session token valid for 24 hours.
     *
     * @param req The login request containing email and password.
     * @return A Mono containing the login response with a JWT token.
     */
    public Mono<LoginResponse> login(LoginRequest req) {
        log.info("Login attempt for email: {}", req.getEmail());
        return platform_repository.findByEmail(req.getEmail())
                .flatMap(platform -> {
                    if (platform.getPassword_hash() == null ||
                            !password_encoder.matches(req.getPassword(), platform.getPassword_hash())) {
                        return Mono.error(new RuntimeException("Incorrect email or password."));
                    }
                    if (Boolean.FALSE.equals(platform.getEmail_verified())) {
                        return Mono.error(
                                new IllegalStateException("Please verify your email before logging in."));
                    }
                    if (Boolean.FALSE.equals(platform.getActive())) {
                        return Mono
                                .error(new IllegalStateException(
                                        "This account has been deactivated. Contact support."));
                    }

                    String token = jwt_service.generateToken(
                            platform.getEmail(), platform.getId(), platform.getName(), "ROLE_PLATFORM");

                    return Mono.just(LoginResponse.builder()
                            .token(token)
                            .platformId(platform.getId())
                            .name(platform.getName())
                            .email(platform.getEmail())
                            .emailVerified(platform.getEmail_verified())
                            .active(platform.getActive())
                            .build());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No platform found for {}, checking super_admins...", req.getEmail());
                    return super_admin_repository.findByEmail(req.getEmail())
                            .flatMap(admin -> {
                                log.info("Found SuperAdmin record for {}: id={}, name={}",
                                        admin.getEmail(), admin.getId(), admin.getName());

                                if (admin.getPassword_hash() == null) {
                                    log.warn("SuperAdmin {} has no password hash!", admin.getEmail());
                                    return Mono.error(new RuntimeException("Incorrect email or password."));
                                }

                                if (!password_encoder.matches(req.getPassword(), admin.getPassword_hash())) {
                                    log.warn("Password mismatch for SuperAdmin {}", admin.getEmail());
                                    return Mono.error(new RuntimeException("Incorrect email or password."));
                                }

                                log.info("SuperAdmin {} authenticated successfully", admin.getEmail());
                                String token = jwt_service.generateToken(
                                        admin.getEmail(), -1L, admin.getName(), "ROLE_SUPERADMIN");

                                return Mono.just(LoginResponse.builder()
                                        .token(token)
                                        .platformId(-1L)
                                        .name(admin.getName())
                                        .email(admin.getEmail())
                                        .emailVerified(admin.getEmail_verified())
                                        .active(true)
                                        .build());
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("No SuperAdmin found for email: {}", req.getEmail());
                                return Mono.error(new RuntimeException("Incorrect email or password."));
                            }));
                }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. RESEND OTP (for accounts that haven't verified yet)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resends the verification OTP to the platform's email.
     *
     * @param email The email address to send the OTP to.
     * @return A Mono that completes when the OTP email is sent.
     */
    public Mono<Void> resendOtp(String email) {
        return platform_repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("Account not found.")))
                .flatMap(platform -> {
                    if (Boolean.TRUE.equals(platform.getEmail_verified())) {
                        return Mono.error(new IllegalStateException("This account is already verified."));
                    }
                    String otp = generateOtp();
                    platform.setOtp_code(otp);
                    platform.setOtp_expiry(LocalDateTime.now().plusMinutes(15));
                    platform.setUpdated_at(LocalDateTime.now());
                    return platform_repository.save(platform)
                            .then(email_service.sendOtp(email, otp, platform.getName()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REGENERATE API KEY (step 1: request OTP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates API key regeneration by sending an OTP.
     * The platform must confirm with the OTP to receive the new key.
     *
     * @param platform_id The identifier of the platform requesting regeneration.
     * @return A Mono that completes when the OTP email is sent.
     */
    public Mono<Void> requestApiKeyRegeneration(Long platform_id) {
        return platform_repository.findById(platform_id)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found.")))
                .flatMap(platform -> {
                    String otp = generateOtp();
                    platform.setOtp_code(otp);
                    platform.setOtp_expiry(LocalDateTime.now().plusMinutes(15));
                    platform.setUpdated_at(LocalDateTime.now());
                    return platform_repository.save(platform)
                            .then(email_service.sendOtp(platform.getEmail(), otp, platform.getName()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REGENERATE API KEY (step 2: confirm OTP → return new raw key)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirms the API key regeneration with an OTP.
     *
     * @param platform_id The identifier of the platform.
     * @param code        The OTP code to validate.
     * @return A Mono containing the response with the new API key.
     */
    public Mono<EmailVerificationResponse> confirmApiKeyRegeneration(Long platform_id, String code) {
        return platform_repository.findById(platform_id)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found.")))
                .flatMap(platform -> {
                    if (platform.getOtp_code() == null || !platform.getOtp_code().equals(code)) {
                        return Mono.error(new RuntimeException("Invalid OTP code."));
                    }
                    if (platform.getOtp_expiry() == null || platform.getOtp_expiry().isBefore(LocalDateTime.now())) {
                        return Mono.error(new RuntimeException("OTP code expired."));
                    }

                    String raw_key = UUID.randomUUID().toString();
                    String hashed_key = SecurityUtils.hashApiKey(raw_key);

                    platform.setApi_key(hashed_key);
                    platform.setOtp_code(null);
                    platform.setOtp_expiry(null);
                    platform.setUpdated_at(LocalDateTime.now());

                    return platform_repository.save(platform)
                            .then(email_service.sendApiKeyRegeneratedNotification(platform.getEmail(),
                                    platform.getName()))
                            .thenReturn(EmailVerificationResponse.builder()
                                    .message("New API key generated. Keep it safe, it will not be shown again.")
                                    .apiKey(raw_key)
                                    .platformId(platform.getId())
                                    .name(platform.getName())
                                    .email(platform.getEmail())
                                    .build());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates the password reset flow by sending a reset code.
     *
     * @param email The email address associated with the account.
     * @return A Mono that completes when the reset email is sent.
     */
    public Mono<Void> forgotPassword(String email) {
        return platform_repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("No account associated with this email.")))
                .flatMap(platform -> {
                    String code = generateOtp();
                    platform.setReset_code(code);
                    platform.setReset_code_expiry(LocalDateTime.now().plusMinutes(15));
                    platform.setReset_attempts(0);
                    platform.setUpdated_at(LocalDateTime.now());
                    return platform_repository.save(platform)
                            .then(email_service.sendPasswordReset(email, code, platform.getName()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resets the platform's password using a valid reset code.
     *
     * @param req The reset request containing email, code, and new password.
     * @return A Mono that completes when the password is reset.
     */
    public Mono<Void> resetPassword(ResetPasswordRequest req) {
        return platform_repository.findByEmail(req.getEmail())
                .switchIfEmpty(Mono.error(new RuntimeException("Account not found.")))
                .flatMap(platform -> {
                    int attempts = platform.getReset_attempts() == null ? 0 : platform.getReset_attempts();
                    if (attempts >= 3) {
                        return Mono.error(new IllegalStateException("Too many attempts. Request a new code."));
                    }
                    if (platform.getReset_code() == null || !platform.getReset_code().equals(req.getCode())) {
                        platform.setReset_attempts(attempts + 1);
                        return platform_repository.save(platform)
                                .then(Mono.error(new RuntimeException("Invalid reset code.")));
                    }
                    if (platform.getReset_code_expiry() == null ||
                            platform.getReset_code_expiry().isBefore(LocalDateTime.now())) {
                        return Mono.error(new RuntimeException("Code expired. Please request a new one."));
                    }

                    platform.setPassword_hash(password_encoder.encode(req.getNewPassword()));
                    platform.setReset_code(null);
                    platform.setReset_code_expiry(null);
                    platform.setReset_attempts(0);
                    platform.setUpdated_at(LocalDateTime.now());

                    return platform_repository.save(platform)
                            .then(email_service.sendPasswordChangedNotification(req.getEmail(), platform.getName()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. CHANGE PASSWORD (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the authenticated platform's password.
     *
     * @param platform_id The identifier of the platform.
     * @param req         The change password request containing old and new
     *                    passwords.
     * @return A Mono that completes when the password is changed.
     */
    public Mono<Void> changePassword(Long platform_id, ChangePasswordRequest req) {
        return platform_repository.findById(platform_id)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found.")))
                .flatMap(platform -> {
                    if (!password_encoder.matches(req.getCurrentPassword(), platform.getPassword_hash())) {
                        return Mono.error(new RuntimeException("Incorrect current password."));
                    }
                    platform.setPassword_hash(password_encoder.encode(req.getNewPassword()));
                    platform.setUpdated_at(LocalDateTime.now());
                    return platform_repository.save(platform)
                            .then(email_service.sendPasswordChangedNotification(platform.getEmail(),
                                    platform.getName()));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. GET PROFILE (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the profile information of the authenticated platform.
     *
     * @param platform_id The identifier of the platform.
     * @return A Mono containing the platform profile response.
     */
    public Mono<PlatformResponse> getProfile(Long platform_id) {
        return platform_repository.findById(platform_id)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found.")))
                .map(p -> PlatformResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .email(p.getEmail())
                        .active(p.getActive())
                        .createdAt(p.getCreated_at())
                        .updatedAt(p.getUpdated_at())
                        .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
