package com.projects.application.service.platform;

import com.projects.application.port.in.platform.ManagePlatformUseCase;
import com.projects.application.port.out.EmailServicePort;
import com.projects.application.port.out.PlatformRepositoryPort;
import com.projects.adapter.in.web.dto.*;
import com.projects.adapter.out.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Application use case — Profile, password, and API key management.
 */
@Service
@RequiredArgsConstructor
public class ManagePlatformUseCaseImpl implements ManagePlatformUseCase {

    private final PlatformRepositoryPort platformRepository;
    private final EmailServicePort emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Random random = new Random();

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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
                        .message("Nouvelle clé API générée. Conservez-la en lieu sûr.")
                        .apiKey(rawKey)
                        .platformId(platform.getId())
                        .name(platform.getName())
                        .email(platform.getEmail())
                        .build());
            });
    }

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
