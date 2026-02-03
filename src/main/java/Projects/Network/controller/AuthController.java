package Projects.Network.controller;

import Projects.Network.model.User;
import Projects.Network.service.AuthService;
import Projects.Network.service.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword())
                .map(token -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
                });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        return authService.register(user)
                .map(saved -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Code de vérification envoyé");
                    response.put("email", saved.getEmail());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                });
    }

    @PostMapping("/verify-email")
    public Mono<ResponseEntity<Map<String, Object>>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmailAndComplete(request.getEmail(), request.getCode())
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Email vérifié");
                    response.put("token", jwtService.generateToken(user));
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Map<String, String>>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendVerificationCode(request.getEmail())
                .thenReturn(ResponseEntity.ok(Map.of("message", "Code renvoyé")));
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        @Size(min = 6)
        private String password;
        @NotBlank
        private String firstName;
        @NotBlank
        private String lastName;
    }

    @Data
    public static class VerifyEmailRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        @Size(min = 6, max = 6)
        private String code;
    }

    @Data
    public static class ResendVerificationRequest {
        @NotBlank
        @Email
        private String email;
    }
}