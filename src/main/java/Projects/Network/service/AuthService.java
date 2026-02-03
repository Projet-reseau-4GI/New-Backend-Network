package Projects.Network.service;

import Projects.Network.model.User;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    public Mono<User> register(User user) {
        return userRepository.existsByEmail(user.getEmail())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RuntimeException("EMAIL_ALREADY_EXISTS"));
                    }
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    user.setEmailVerified(false);
                    return userRepository.save(user)
                            .flatMap(saved -> emailVerificationService.generateAndSendCode(saved).thenReturn(saved));
                });
    }

    public Mono<User> verifyEmailAndComplete(String email, String code) {
        return emailVerificationService.verifyEmailAndComplete(email, code);
    }

    public Mono<String> login(String email, String password) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("USER_NOT_FOUND")))
                .flatMap(user -> {
                    if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                        return Mono.error(new RuntimeException("EMAIL_NOT_VERIFIED"));
                    }
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                    return Mono.just(jwtService.generateToken(user));
                });
    }

    public Mono<Void> resendVerificationCode(String email) {
        return emailVerificationService.resendVerificationCode(email);
    }
}
