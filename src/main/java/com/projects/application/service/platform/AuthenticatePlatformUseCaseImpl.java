package com.projects.application.service.platform;

import com.projects.application.port.in.platform.AuthenticatePlatformUseCase;
import com.projects.application.port.out.PlatformRepositoryPort;
import com.projects.application.port.out.TokenServicePort;
import com.projects.adapter.in.web.dto.LoginRequest;
import com.projects.adapter.in.web.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application use case — Platform login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticatePlatformUseCaseImpl implements AuthenticatePlatformUseCase {

    private final PlatformRepositoryPort platformRepository;
    private final TokenServicePort tokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
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

                String token = tokenService.generateToken(
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
}
