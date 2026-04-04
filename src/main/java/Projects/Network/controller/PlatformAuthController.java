package Projects.Network.controller;

import Projects.Network.dto.OtpRequest;
import Projects.Network.dto.OtpVerification;
import Projects.Network.service.PlatformAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    @PostMapping("/request")
    public Mono<Void> requestOtp(@RequestBody OtpRequest request) {
        log.info("OTP request for email: {}", request.getEmail());
        return platformAuthService.requestOtp(request.getEmail());
    }

    @PostMapping("/verify")
    public Mono<String> verifyOtp(@RequestBody OtpVerification verification) {
        log.info("OTP verification for email: {}", verification.getEmail());
        return platformAuthService.verifyOtp(verification.getEmail(), verification.getCode());
    }
}

