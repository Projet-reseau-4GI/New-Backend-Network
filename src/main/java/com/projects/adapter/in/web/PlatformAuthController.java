package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.*;
import com.projects.application.port.in.platform.AuthenticatePlatformUseCase;
import com.projects.application.port.in.platform.ManagePlatformUseCase;
import com.projects.application.port.in.platform.RegisterPlatformUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Inbound adapter (Web) — Platform authentication and account management.
 * Injects use case interfaces (ports) — no direct service coupling.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Authentification", description = "Inscription, connexion, gestion de compte et de clé API")
public class PlatformAuthController {

    private final RegisterPlatformUseCase registerUseCase;
    private final AuthenticatePlatformUseCase authenticateUseCase;
    private final ManagePlatformUseCase manageUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscrire une nouvelle plateforme",
               description = "Crée le compte, génère un OTP envoyé par email. Le compte reste inactif jusqu'à la vérification.")
    public Mono<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Register request for email: {}", req.getEmail());
        return registerUseCase.register(req)
            .thenReturn(Map.of("message", "Compte créé. Un code de vérification a été envoyé à " + req.getEmail() + "."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Vérifier l'email via OTP",
               description = "Valide le code OTP reçu par email. Retourne la clé API raw (à afficher une seule fois).")
    public Mono<EmailVerificationResponse> verifyEmail(@RequestBody OtpVerification req) {
        log.info("Email verification for: {}", req.getEmail());
        return registerUseCase.verifyEmail(req.getEmail(), req.getCode());
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Renvoyer le code OTP",
               description = "Régénère et renvoie un OTP pour les comptes non encore vérifiés.")
    public Mono<Map<String, String>> resendOtp(@RequestBody OtpRequest req) {
        log.info("Resend OTP for: {}", req.getEmail());
        return registerUseCase.resendOtp(req.getEmail())
            .thenReturn(Map.of("message", "Nouveau code envoyé à " + req.getEmail() + "."));
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion au portail",
               description = "Authentifie avec email+mot de passe. Retourne un JWT de session (24h).")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("Login attempt for: {}", req.getEmail());
        return authenticateUseCase.login(req);
    }

    @GetMapping("/me")
    @Operation(summary = "Consulter son profil",
               description = "Retourne les informations du compte authentifié.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<PlatformResponse> getProfile(Authentication auth) {
        return manageUseCase.getProfile(extractPlatformId(auth));
    }

    @PutMapping("/change-password")
    @Operation(summary = "Modifier le mot de passe",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        return manageUseCase.changePassword(extractPlatformId(auth), req)
            .thenReturn(Map.of("message", "Mot de passe modifié avec succès."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié",
               description = "Envoie un code de réinitialisation par email.")
    public Mono<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        log.info("Password reset requested for: {}", req.getEmail());
        return manageUseCase.forgotPassword(req.getEmail())
            .thenReturn(Map.of("message", "Code de réinitialisation envoyé à " + req.getEmail() + "."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe",
               description = "Valide le code reçu par email et enregistre le nouveau mot de passe.")
    public Mono<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return manageUseCase.resetPassword(req)
            .thenReturn(Map.of("message", "Mot de passe réinitialisé avec succès. Vous pouvez vous connecter."));
    }

    @PostMapping("/regenerate-token")
    @Operation(summary = "Demander la régénération de la clé API",
               description = "Envoie un OTP par email. Confirmez ensuite avec /confirm-regenerate.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> requestApiKeyRegeneration(Authentication auth) {
        return manageUseCase.requestApiKeyRegeneration(extractPlatformId(auth))
            .thenReturn(Map.of("message", "Un code de confirmation a été envoyé par email."));
    }

    @PostMapping("/confirm-regenerate")
    @Operation(summary = "Confirmer la régénération de la clé API",
               description = "Valide l'OTP et retourne la nouvelle clé API raw (affichée une seule fois).",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<EmailVerificationResponse> confirmApiKeyRegeneration(
            @RequestBody OtpVerification req, Authentication auth) {
        return manageUseCase.confirmApiKeyRegeneration(extractPlatformId(auth), req.getCode());
    }

    private Long extractPlatformId(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié.");
        Object cred = auth.getCredentials();
        if (cred instanceof Long id) return id;
        if (cred instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide.");
    }
}
