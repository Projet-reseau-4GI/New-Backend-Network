package Projects.Network.service;

import Projects.Network.dto.*;
import Projects.Network.model.Platform;
import Projects.Network.repository.PlatformRepository;
import Projects.Network.utils.SecurityUtils;
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
 *  1. register()         → create platform (inactive), send OTP by email
 *  2. verifyEmail()      → validate OTP, activate platform, return raw API key to frontend
 *  3. login()            → check email+password, return JWT session token
 *  4. regenerateApiKey() → (JWT protected) send OTP by email first
 *  5. confirmRegenerateApiKey() → validate OTP, generate new API key, return it to frontend
 *  6. forgotPassword()   → send reset code by email
 *  7. resetPassword()    → validate code, save new hashed password
 *  8. changePassword()   → (JWT protected) validate old password, save new hash
 *  9. getProfile()       → (JWT protected) return platform info
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAuthService {

    private final PlatformRepository platformRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
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
     */
    public Mono<Void> register(RegisterRequest req) {
        return platformRepository.findByEmail(req.getEmail())
            .flatMap(existing -> Mono.<Void>error(
                new IllegalArgumentException("Un compte avec cet email existe déjà.")))
            .switchIfEmpty(Mono.defer(() -> {
                String otp   = generateOtp();
                // Temporary placeholder API key — will be replaced after email verification
                String rawKey = UUID.randomUUID().toString();
                String hashedKey = SecurityUtils.hashApiKey(rawKey);

                Platform platform = Platform.builder()
                    .name(req.getName())
                    .email(req.getEmail())
                    .passwordHash(passwordEncoder.encode(req.getPassword()))
                    .apiKey(hashedKey)
                    .otpCode(otp)
                    .otpExpiry(LocalDateTime.now().plusMinutes(15))
                    .emailVerified(false)
                    .resetAttempts(0)
                    .active(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

                return platformRepository.save(platform)
                    .then(emailService.sendOtp(req.getEmail(), otp, req.getName()));
            }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VERIFY EMAIL (OTP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verifies the OTP sent during registration.
     * On success:
     *   - Marks email as verified, activates the platform.
     *   - Generates a fresh API key.
     *   - Returns the raw API key to the frontend (displayed once, never re-sent).
     */
    public Mono<EmailVerificationResponse> verifyEmail(String email, String code) {
        return platformRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Compte introuvable.")))
            .flatMap(platform -> {
                if (platform.getEmailVerified() != null && platform.getEmailVerified()) {
                    return Mono.error(new IllegalStateException("Ce compte est déjà vérifié."));
                }
                if (platform.getOtpCode() == null || !platform.getOtpCode().equals(code)) {
                    return Mono.error(new RuntimeException("Code OTP invalide."));
                }
                if (platform.getOtpExpiry() == null || platform.getOtpExpiry().isBefore(LocalDateTime.now())) {
                    return Mono.error(new RuntimeException("Code OTP expiré. Veuillez en demander un nouveau."));
                }

                // Generate the definitive API key
                String rawKey    = UUID.randomUUID().toString();
                String hashedKey = SecurityUtils.hashApiKey(rawKey);

                platform.setApiKey(hashedKey);
                platform.setOtpCode(null);
                platform.setOtpExpiry(null);
                platform.setEmailVerified(true);
                platform.setActive(true);
                platform.setUpdatedAt(LocalDateTime.now());

                return platformRepository.save(platform)
                    .map(saved -> EmailVerificationResponse.builder()
                        .message("Compte activé avec succès. Conservez votre clé API en lieu sûr, elle ne sera plus affichée.")
                        .apiKey(rawKey)
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
     */
    public Mono<LoginResponse> login(LoginRequest req) {
        return platformRepository.findByEmail(req.getEmail())
            .switchIfEmpty(Mono.error(new RuntimeException("Email ou mot de passe incorrect.")))
            .flatMap(platform -> {
                if (platform.getPasswordHash() == null ||
                    !passwordEncoder.matches(req.getPassword(), platform.getPasswordHash())) {
                    return Mono.error(new RuntimeException("Email ou mot de passe incorrect."));
                }
                if (Boolean.FALSE.equals(platform.getEmailVerified())) {
                    return Mono.error(new IllegalStateException("Veuillez vérifier votre email avant de vous connecter."));
                }
                if (Boolean.FALSE.equals(platform.getActive())) {
                    return Mono.error(new IllegalStateException("Ce compte a été désactivé. Contactez le support."));
                }

                String token = jwtService.generateToken(
                    platform.getEmail(), platform.getId(), platform.getName());

                return Mono.just(LoginResponse.builder()
                    .token(token)
                    .platformId(platform.getId())
                    .name(platform.getName())
                    .email(platform.getEmail())
                    .emailVerified(platform.getEmailVerified())
                    .active(platform.getActive())
                    .build());
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. RESEND OTP (for accounts that haven't verified yet)
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> resendOtp(String email) {
        return platformRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Compte introuvable.")))
            .flatMap(platform -> {
                if (Boolean.TRUE.equals(platform.getEmailVerified())) {
                    return Mono.error(new IllegalStateException("Ce compte est déjà vérifié."));
                }
                String otp = generateOtp();
                platform.setOtpCode(otp);
                platform.setOtpExpiry(LocalDateTime.now().plusMinutes(15));
                platform.setUpdatedAt(LocalDateTime.now());
                return platformRepository.save(platform)
                    .then(emailService.sendOtp(email, otp, platform.getName()));
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REGENERATE API KEY (step 1: request OTP)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates API key regeneration by sending an OTP.
     * The platform must confirm with the OTP to receive the new key.
     */
    public Mono<Void> requestApiKeyRegeneration(Long platformId) {
        return platformRepository.findById(platformId)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable.")))
            .flatMap(platform -> {
                String otp = generateOtp();
                platform.setOtpCode(otp);
                platform.setOtpExpiry(LocalDateTime.now().plusMinutes(15));
                platform.setUpdatedAt(LocalDateTime.now());
                return platformRepository.save(platform)
                    .then(emailService.sendOtp(platform.getEmail(), otp, platform.getName()));
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. REGENERATE API KEY (step 2: confirm OTP → return new raw key)
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<EmailVerificationResponse> confirmApiKeyRegeneration(Long platformId, String code) {
        return platformRepository.findById(platformId)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable.")))
            .flatMap(platform -> {
                if (platform.getOtpCode() == null || !platform.getOtpCode().equals(code)) {
                    return Mono.error(new RuntimeException("Code OTP invalide."));
                }
                if (platform.getOtpExpiry() == null || platform.getOtpExpiry().isBefore(LocalDateTime.now())) {
                    return Mono.error(new RuntimeException("Code OTP expiré."));
                }

                String rawKey    = UUID.randomUUID().toString();
                String hashedKey = SecurityUtils.hashApiKey(rawKey);

                platform.setApiKey(hashedKey);
                platform.setOtpCode(null);
                platform.setOtpExpiry(null);
                platform.setUpdatedAt(LocalDateTime.now());

                return platformRepository.save(platform)
                    .then(emailService.sendApiKeyRegeneratedNotification(platform.getEmail(), platform.getName()))
                    .thenReturn(EmailVerificationResponse.builder()
                        .message("Nouvelle clé API générée. Conservez-la en lieu sûr, elle ne sera plus affichée.")
                        .apiKey(rawKey)
                        .platformId(platform.getId())
                        .name(platform.getName())
                        .email(platform.getEmail())
                        .build());
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> forgotPassword(String email) {
        return platformRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Aucun compte associé à cet email.")))
            .flatMap(platform -> {
                String code = generateOtp();
                platform.setResetCode(code);
                platform.setResetCodeExpiry(LocalDateTime.now().plusMinutes(15));
                platform.setResetAttempts(0);
                platform.setUpdatedAt(LocalDateTime.now());
                return platformRepository.save(platform)
                    .then(emailService.sendPasswordReset(email, code, platform.getName()));
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> resetPassword(ResetPasswordRequest req) {
        return platformRepository.findByEmail(req.getEmail())
            .switchIfEmpty(Mono.error(new RuntimeException("Compte introuvable.")))
            .flatMap(platform -> {
                int attempts = platform.getResetAttempts() == null ? 0 : platform.getResetAttempts();
                if (attempts >= 3) {
                    return Mono.error(new IllegalStateException("Trop de tentatives. Demandez un nouveau code."));
                }
                if (platform.getResetCode() == null || !platform.getResetCode().equals(req.getCode())) {
                    platform.setResetAttempts(attempts + 1);
                    return platformRepository.save(platform)
                        .then(Mono.error(new RuntimeException("Code de réinitialisation invalide.")));
                }
                if (platform.getResetCodeExpiry() == null ||
                    platform.getResetCodeExpiry().isBefore(LocalDateTime.now())) {
                    return Mono.error(new RuntimeException("Code expiré. Veuillez en demander un nouveau."));
                }

                platform.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
                platform.setResetCode(null);
                platform.setResetCodeExpiry(null);
                platform.setResetAttempts(0);
                platform.setUpdatedAt(LocalDateTime.now());

                return platformRepository.save(platform)
                    .then(emailService.sendPasswordChangedNotification(req.getEmail(), platform.getName()));
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. CHANGE PASSWORD (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<Void> changePassword(Long platformId, ChangePasswordRequest req) {
        return platformRepository.findById(platformId)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable.")))
            .flatMap(platform -> {
                if (!passwordEncoder.matches(req.getCurrentPassword(), platform.getPasswordHash())) {
                    return Mono.error(new RuntimeException("Mot de passe actuel incorrect."));
                }
                platform.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
                platform.setUpdatedAt(LocalDateTime.now());
                return platformRepository.save(platform)
                    .then(emailService.sendPasswordChangedNotification(platform.getEmail(), platform.getName()));
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. GET PROFILE (authenticated)
    // ─────────────────────────────────────────────────────────────────────────

    public Mono<PlatformResponse> getProfile(Long platformId) {
        return platformRepository.findById(platformId)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable.")))
            .map(p -> PlatformResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .email(p.getEmail())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS (legacy OTP kept for backward compat)
    // ─────────────────────────────────────────────────────────────────────────

    /** @deprecated Use register() + verifyEmail() flow instead */
    @Deprecated
    public Mono<Void> requestOtp(String email) {
        return platformRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable avec l'email : " + email)))
            .flatMap(platform -> {
                String code = generateOtp();
                platform.setOtpCode(code);
                platform.setOtpExpiry(LocalDateTime.now().plusMinutes(15));
                return platformRepository.save(platform)
                    .then(emailService.sendOtp(email, code, platform.getName()));
            });
    }

    /** @deprecated Use verifyEmail() instead */
    @Deprecated
    public Mono<String> verifyOtp(String email, String code) {
        return platformRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Plateforme introuvable")))
            .flatMap(platform -> {
                if (platform.getOtpCode() == null || !platform.getOtpCode().equals(code)) {
                    return Mono.error(new RuntimeException("Code OTP invalide"));
                }
                if (platform.getOtpExpiry() == null || platform.getOtpExpiry().isBefore(LocalDateTime.now())) {
                    return Mono.error(new RuntimeException("Code OTP expiré"));
                }
                platform.setOtpCode(null);
                platform.setOtpExpiry(null);
                String rawApiKey = UUID.randomUUID().toString();
                platform.setApiKey(SecurityUtils.hashApiKey(rawApiKey));
                platform.setUpdatedAt(LocalDateTime.now());
                return platformRepository.save(platform).thenReturn(rawApiKey);
            });
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
