package Projects.Network.service;

import reactor.core.publisher.Mono;

public interface EmailService {
    Mono<Void> sendPasswordResetCode(String to, String code);

    Mono<Void> sendEmailVerificationCode(String to, String code);
}
