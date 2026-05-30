package com.projects.application.service.platform;

import com.projects.domain.model.Platform;
import com.projects.application.port.in.platform.RegisterPlatformUseCase;
import com.projects.application.port.out.EmailServicePort;
import com.projects.application.port.out.PlatformRepositoryPort;
import com.projects.adapter.in.web.dto.EmailVerificationResponse;
import com.projects.adapter.in.web.dto.RegisterRequest;
import com.projects.adapter.out.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Application use case — Platform registration and email verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterPlatformUseCaseImpl implements RegisterPlatformUseCase {

    private final PlatformRepositoryPort platformRepository;
    private final EmailServicePort emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Random random = new Random();

    @Override
    public Mono<Void> register(RegisterRequest req) {
        return platformRepository.findByEmail(req.getEmail())
            .flatMap(existing -> Mono.<Void>error(
                new IllegalArgumentException("Un compte avec cet email existe déjà.")))
            .switchIfEmpty(Mono.defer(() -> {
                String otp = generateOtp();
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

    @Override
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
                        .message("Compte activé avec succès. Conservez votre clé API en lieu sûr.")
                        .apiKey(rawKey)
                        .platformId(saved.getId())
                        .name(saved.getName())
                        .email(saved.getEmail())
                        .build());
            });
    }

    @Override
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

    private String generateOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
