package Projects.Network.service;

import Projects.Network.model.PasswordResetToken;
import Projects.Network.repository.PasswordResetTokenRepository;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {
    
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Value("${app.password-reset.code-length:6}")
    private int codeLength;
    
    @Value("${app.password-reset.expiry-minutes:15}")
    private int expiryMinutes;
    
    @Value("${app.password-reset.max-attempts:3}")
    private int maxRetryAttempts;

    public Mono<Void> processForgotPassword(String email) {
        log.info("Traitement de la demande de réinitialisation pour: {}", email);
        
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.defer(() -> {
                log.warn("Tentative de réinitialisation pour un email inexistant: {}", email);
                return Mono.error(new RuntimeException("Si cet email existe, un code a été envoyé"));
            }))
            .flatMap(user -> {
                String code = generateSecureCode();
                Instant expiry = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);
                
                // Ne créer que les champs qui existent dans la table
                PasswordResetToken tokenEntity = PasswordResetToken.builder()
                    .token(code)
                    .userId(user.getUserId())
                    .expiryDate(expiry)
                    .build();
                
                log.debug("Token créé: code={}, userId={}, expiry={}", code, user.getUserId(), expiry);
                
                return tokenRepository.deleteByUserId(user.getUserId())
                    .then(tokenRepository.save(tokenEntity))
                    .doOnSuccess(saved -> log.info("Token sauvegardé avec succès: {}", saved.getTokenId()))
                    .doOnError(error -> log.error("Erreur lors de la sauvegarde du token: {}", error.getMessage(), error))
                    .flatMap(savedToken -> sendEmailWithRetry(email, code))
                    .doOnSuccess(v -> log.info("Code de réinitialisation créé et envoyé pour: {}", email))
                    .doOnError(e -> log.error("Erreur lors du traitement pour {}: {}", email, e.getMessage(), e));
            });
    }
    
    private Mono<Void> sendEmailWithRetry(String email, String code) {
        return emailService.sendPasswordResetCode(email, code)
            .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofSeconds(2))
                .filter(throwable -> throwable instanceof RuntimeException)
                .doBeforeRetry(retrySignal -> 
                    log.warn("Nouvelle tentative d'envoi d'email ({}/{})", 
                        retrySignal.totalRetries() + 1, maxRetryAttempts))
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> 
                    new RuntimeException("Impossible d'envoyer l'email après " + 
                        maxRetryAttempts + " tentatives")));
    }

    private String generateSecureCode() {
        int bound = (int) Math.pow(10, codeLength);
        int code = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + codeLength + "d", code);
    }

    public Mono<Boolean> verifyCode(String email, String code) {
        log.debug("Vérification du code pour: {}", email);
        
        if (code == null || code.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        return userRepository.findByEmail(email)
            .flatMap(user -> tokenRepository.findByUserId(user.getUserId()))
            .filter(token -> {
                boolean match = token.getToken().equals(code.trim());
                boolean notExpired = token.getExpiryDate().isAfter(Instant.now());
                
                if (!match) {
                    log.warn("Code incorrect pour: {}", email);
                }
                if (!notExpired) {
                    log.warn("Code expiré pour: {}", email);
                }
                
                return match && notExpired;
            })
            .map(token -> true)
            .defaultIfEmpty(false)
            .doOnNext(valid -> {
                if (valid) {
                    log.info("Code vérifié avec succès pour: {}", email);
                }
            });
    }

    public Mono<Void> resetPassword(String email, String code, String newPassword) {
        log.info("Tentative de réinitialisation de mot de passe pour: {}", email);
        
        if (newPassword == null || newPassword.length() < 8) {
            return Mono.error(new IllegalArgumentException(
                "Le mot de passe doit contenir au moins 8 caractères"));
        }
        
        return verifyCode(email, code)
            .flatMap(valid -> {
                if (!valid) {
                    return Mono.error(new RuntimeException("Code invalide ou expiré"));
                }
                return userRepository.findByEmail(email);
            })
            .flatMap(user -> {
                String encodedPassword = passwordEncoder.encode(newPassword);
                user.setPassword(encodedPassword);
                return userRepository.save(user);
            })
            .flatMap(savedUser -> tokenRepository.deleteByUserId(savedUser.getUserId()))
            .doOnSuccess(v -> log.info("Mot de passe réinitialisé avec succès pour: {}", email))
            .doOnError(e -> log.error("Erreur lors de la réinitialisation pour {}: {}", 
                email, e.getMessage()));
    }
}