package Projects.Network.controller;

import Projects.Network.dto.*;
import Projects.Network.service.JwtService;
import Projects.Network.service.PlatformAuthService;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Authentification", description = "Inscription, connexion, gestion de compte et de clé API")
public class PlatformAuthController {

    private final PlatformAuthService authService;
    private final JwtService jwtService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. REGISTER
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscrire une nouvelle plateforme",
               description = "Crée le compte, génère un OTP envoyé par email. Le compte reste inactif jusqu'à la vérification.")
    public Mono<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Register request for email: {}", req.getEmail());
        return authService.register(req)
            .thenReturn(Map.of(
                "message", "Compte créé. Un code de vérification a été envoyé à " + req.getEmail() + "."
            ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VERIFY EMAIL
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(summary = "Vérifier l'email via OTP",
               description = "Valide le code OTP reçu par email. Retourne la clé API raw (à afficher une seule fois).")
    public Mono<EmailVerificationResponse> verifyEmail(@RequestBody OtpVerification req) {
        log.info("Email verification for: {}", req.getEmail());
        return authService.verifyEmail(req.getEmail(), req.getCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. RESEND OTP
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/resend-otp")
    @Operation(summary = "Renvoyer le code OTP",
               description = "Régénère et renvoie un OTP pour les comptes non encore vérifiés.")
    public Mono<Map<String, String>> resendOtp(@RequestBody OtpRequest req) {
        log.info("Resend OTP for: {}", req.getEmail());
        return authService.resendOtp(req.getEmail())
            .thenReturn(Map.of("message", "Nouveau code envoyé à " + req.getEmail() + "."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Connexion au portail",
               description = "Authentifie avec email+mot de passe. Retourne un JWT de session (24h).")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("Login attempt for: {}", req.getEmail());
        return authService.login(req);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GET PROFILE (JWT protected)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Consulter son profil",
               description = "Retourne les informations du compte authentifié.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<PlatformResponse> getProfile(Authentication auth) {
        Long platformId = extractPlatformId(auth);
        return authService.getProfile(platformId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. CHANGE PASSWORD (JWT protected)
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/change-password")
    @Operation(summary = "Modifier le mot de passe",
               description = "Valide l'ancien mot de passe puis enregistre le nouveau.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication auth) {
        Long platformId = extractPlatformId(auth);
        return authService.changePassword(platformId, req)
            .thenReturn(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. FORGOT PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié",
               description = "Envoie un code de réinitialisation par email.")
    public Mono<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        log.info("Password reset requested for: {}", req.getEmail());
        return authService.forgotPassword(req.getEmail())
            .thenReturn(Map.of("message", "Code de réinitialisation envoyé à " + req.getEmail() + "."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. RESET PASSWORD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe",
               description = "Valide le code reçu par email et enregistre le nouveau mot de passe.")
    public Mono<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req)
            .thenReturn(Map.of("message", "Mot de passe réinitialisé avec succès. Vous pouvez vous connecter."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. REQUEST API KEY REGENERATION (JWT protected — sends OTP first)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/regenerate-token")
    @Operation(summary = "Demander la régénération de la clé API",
               description = "Envoie un OTP par email. Confirmez ensuite avec /confirm-regenerate.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> requestApiKeyRegeneration(Authentication auth) {
        Long platformId = extractPlatformId(auth);
        return authService.requestApiKeyRegeneration(platformId)
            .thenReturn(Map.of("message", "Un code de confirmation a été envoyé par email."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. CONFIRM API KEY REGENERATION (JWT protected + OTP)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/confirm-regenerate")
    @Operation(summary = "Confirmer la régénération de la clé API",
               description = "Valide l'OTP et retourne la nouvelle clé API raw (affichée une seule fois).",
               security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<EmailVerificationResponse> confirmApiKeyRegeneration(
            @RequestBody OtpVerification req,
            Authentication auth) {
        Long platformId = extractPlatformId(auth);
        return authService.confirmApiKeyRegeneration(platformId, req.getCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private Long extractPlatformId(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié.");
        }
        // credentials holds the platformId (set by JwtAuthenticationFilter)
        Object cred = auth.getCredentials();
        if (cred instanceof Long id) return id;
        if (cred instanceof Number n) return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide.");
    }
}
