package Projects.Network.service;

import reactor.core.publisher.Mono;

public interface EmailService {
    Mono<Void> sendOtp(String to, String code, String platformName);
}
