package Projects.Network.controller;

import Projects.Network.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @Data
    static class ForgotPasswordRequest {
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        private String email;
    }

    @Data
    static class VerifyCodeRequest {
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        private String email;

        @NotBlank(message = "Le code est requis")
        @Pattern(regexp = "\\d{6}", message = "Le code doit contenir 6 chiffres")
        private String code;
    }

    @Data
    static class ResetPasswordRequest {
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        private String email;

        @NotBlank(message = "Le code est requis")
        @Pattern(regexp = "\\d{6}", message = "Le code doit contenir 6 chiffres")
        private String code;

        @NotBlank(message = "Le nouveau mot de passe est requis")
        @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$", message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial")
        private String newPassword;
    }

    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        log.info("Demande de réinitialisation reçue pour: {}", request.getEmail());

        return passwordResetService.processForgotPassword(request.getEmail())
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "message", "Si cet email existe, un code de vérification a été envoyé",
                        "status", "success"))))
                .onErrorResume(e -> {
                    log.error("Erreur lors de l'envoi du code: {}", e.getMessage());
                    if ("USER_NOT_REGISTERED".equals(e.getMessage())) {
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(Map.of(
                                        "message", "Cet utilisateur n'est pas enregistré",
                                        "status", "error")));
                    }
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of(
                                    "message", "Une erreur est survenue. Veuillez réessayer plus tard.",
                                    "status", "error")));
                });
    }

    @PostMapping("/verify-code")
    public Mono<ResponseEntity<Map<String, Object>>> verifyCode(
            @Valid @RequestBody VerifyCodeRequest request) {

        log.info("Vérification du code pour: {}", request.getEmail());

        return passwordResetService.verifyCode(request.getEmail(), request.getCode())
                .map(valid -> {
                    if (valid) {
                        return ResponseEntity.ok(Map.of(
                                "valid", true,
                                "message", "Code valide",
                                "status", "success"));
                    } else {
                        return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(Map.of(
                                        "valid", false,
                                        "message", "Code invalide ou expiré",
                                        "status", "error"));
                    }
                });
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("Réinitialisation du mot de passe pour: {}", request.getEmail());

        return passwordResetService.resetPassword(
                request.getEmail(),
                request.getCode(),
                request.newPassword)
                .then(Mono.just(ResponseEntity.ok(Map.of(
                        "message", "Mot de passe réinitialisé avec succès",
                        "status", "success"))))
                .onErrorResume(e -> {
                    log.error("Erreur lors de la réinitialisation: {}", e.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "message", e.getMessage(),
                                    "status", "error")));
                });
    }
}