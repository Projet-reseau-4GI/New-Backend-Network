package Projects.Network.service;

import Projects.Network.model.Platform;
import Projects.Network.repository.PlatformRepository;
import Projects.Network.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAuthService {

    private final PlatformRepository platformRepository;
    private final EmailService emailService;
    private final Random random = new Random();

    public Mono<Void> requestOtp(String email) {
        return platformRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found with email: " + email)))
                .flatMap(platform -> {
                    String code = String.format("%06d", random.nextInt(1000000));
                    platform.setOtpCode(code);
                    platform.setOtpExpiry(LocalDateTime.now().plusMinutes(15));
                    
                    return platformRepository.save(platform)
                            .then(emailService.sendOtp(email, code, platform.getName()));
                });
    }

    public Mono<String> verifyOtp(String email, String code) {
        return platformRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found")))
                .flatMap(platform -> {
                    if (platform.getOtpCode() == null || !platform.getOtpCode().equals(code)) {
                        return Mono.error(new RuntimeException("Invalid OTP code"));
                    }
                    if (platform.getOtpExpiry() == null || platform.getOtpExpiry().isBefore(LocalDateTime.now())) {
                        return Mono.error(new RuntimeException("OTP code expired"));
                    }

                    // Clear OTP after success
                    platform.setOtpCode(null);
                    platform.setOtpExpiry(null);
                    
                    // Regenerate a fresh raw API Key for the user
                    String rawApiKey = UUID.randomUUID().toString();
                    log.info("Regenerated new raw API Key for platform: {}", platform.getEmail());
                    platform.setApiKey(SecurityUtils.hashApiKey(rawApiKey));
                    platform.setUpdatedAt(LocalDateTime.now());
                    
                    return platformRepository.save(platform)
                            .thenReturn(rawApiKey);
                });
    }
}

