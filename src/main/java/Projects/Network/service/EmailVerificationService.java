package Projects.Network.service;

import Projects.Network.model.EmailVerificationToken;
import Projects.Network.model.User;
import Projects.Network.repository.EmailVerificationTokenRepository;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.email-verification.code-length:6}")
    private int codeLength;

    @Value("${app.email-verification.expiry-minutes:15}")
    private int expiryMinutes;

    private String generateSecureCode() {
        int bound = (int) Math.pow(10, codeLength);
        int code = SECURE_RANDOM.nextInt(bound);
        return String.format("%0" + codeLength + "d", code);
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public Mono<Void> generateAndSendCode(User user) {
        String code = generateSecureCode();
        String hashedCode = hashCode(code);
        Instant expiry = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .tokenHash(hashedCode)
                .userId(user.getUserId())
                .expiryDate(expiry)
                .build();

        return tokenRepository.deleteByUserId(user.getUserId())
                .then(tokenRepository.save(token))
                .flatMap(saved -> emailService.sendEmailVerificationCode(user.getEmail(), code));
    }

    public Mono<User> verifyEmailAndComplete(String email, String code) {
        String hashedInput = hashCode(code.trim());
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(user -> tokenRepository.findByUserId(user.getUserId())
                        .filter(token -> token.getTokenHash().equals(hashedInput)
                                && token.getExpiryDate().isAfter(Instant.now()))
                        .switchIfEmpty(Mono.error(new RuntimeException("INVALID_OR_EXPIRED_CODE")))
                        .flatMap(token -> {
                            user.setEmailVerified(true);
                            return userRepository.save(user)
                                    .flatMap(savedUser -> tokenRepository.deleteByUserId(savedUser.getUserId())
                                            .thenReturn(savedUser));
                        }));
    }

    public Mono<Void> resendVerificationCode(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .switchIfEmpty(Mono.error(new RuntimeException("EMAIL_ALREADY_VERIFIED")))
                .flatMap(this::generateAndSendCode);
    }
}
