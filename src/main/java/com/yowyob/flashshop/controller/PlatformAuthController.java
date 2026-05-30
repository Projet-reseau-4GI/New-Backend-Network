package com.yowyob.flashshop.controller;

import com.yowyob.flashshop.dto.ChangePasswordRequest;
import com.yowyob.flashshop.dto.EmailVerificationResponse;
import com.yowyob.flashshop.dto.ForgotPasswordRequest;
import com.yowyob.flashshop.dto.LoginRequest;
import com.yowyob.flashshop.dto.LoginResponse;
import com.yowyob.flashshop.dto.OtpRequest;
import com.yowyob.flashshop.dto.OtpVerification;
import com.yowyob.flashshop.dto.PlatformResponse;
import com.yowyob.flashshop.dto.RegisterRequest;
import com.yowyob.flashshop.dto.ResetPasswordRequest;
import com.yowyob.flashshop.service.PlatformAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for platform authentication and account management.
 * Handles registration, login, password changes, and API key management.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "Registration, login, account and API key management")
public class PlatformAuthController {

    private final PlatformAuthService auth_service;

    /**
     * Registers a new platform.
     * Starts the account creation process and sends an OTP via email.
     *
     * @param req registration request details
     * @return a Mono containing a success message
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new platform", description = "Creates the account and generates an OTP sent by email. Account remains inactive until verified.")
    public Mono<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Register request for email: {}", req.getEmail());
        return auth_service.register(req)
                .thenReturn(Map.of(
                        "message", "Account created. A verification code has been sent to " + req.getEmail() + "."));
    }

    /**
     * Verifies the platform email using an OTP.
     * Returns the raw API key upon successful verification.
     *
     * @param req OTP verification details
     * @return a Mono containing the verification response with API key
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verify email via OTP", description = "Validates the OTP code received by email. Returns the raw API key (displayed only once).")
    public Mono<EmailVerificationResponse> verifyEmail(@RequestBody OtpVerification req) {
        log.info("Email verification for: {}", req.getEmail());
        return auth_service.verifyEmail(req.getEmail(), req.getCode());
    }

    /**
     * Resends the OTP verification code.
     *
     * @param req OTP resend request details
     * @return a Mono containing a success message
     */
    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP code", description = "Regenerates and resends an OTP for unverified accounts.")
    public Mono<Map<String, String>> resendOtp(@RequestBody OtpRequest req) {
        log.info("Resend OTP for: {}", req.getEmail());
        return auth_service.resendOtp(req.getEmail())
                .thenReturn(Map.of("message", "New code sent to " + req.getEmail() + "."));
    }

    /**
     * Authenticates a platform and returns a session JWT.
     *
     * @param req login request details
     * @return a Mono containing the login response with JWT
     */
    @PostMapping("/login")
    @Operation(summary = "Portal login", description = "Authenticates with email and password. Returns a session JWT (24h validity).")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("Login attempt for: {}", req.getEmail());
        return auth_service.login(req);
    }

    /**
     * Retrieves the profile of the authenticated platform.
     *
     * @param auth the current authentication context
     * @return a Mono containing the platform profile
     */
    @GetMapping("/me")
    @Operation(summary = "View profile", description = "Returns information about the authenticated account.", security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<PlatformResponse> getProfile(Authentication auth) {
        Long platform_id = extractPlatformId(auth);
        return auth_service.getProfile(platform_id);
    }

    /**
     * Changes the platform password.
     *
     * @param req  password change request details
     * @param auth the current authentication context
     * @return a Mono containing a success message
     */
    @PutMapping("/change-password")
    @Operation(summary = "Change password", description = "Validates the old password and saves the new one.", security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication auth) {
        Long platform_id = extractPlatformId(auth);
        return auth_service.changePassword(platform_id, req)
                .thenReturn(Map.of("message", "Password modified successfully."));
    }

    /**
     * Initiates the forgot password process.
     *
     * @param req forgot password request details
     * @return a Mono containing a success message
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends a reset code via email.")
    public Mono<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        log.info("Password reset requested for: {}", req.getEmail());
        return auth_service.forgotPassword(req.getEmail())
                .thenReturn(Map.of("message", "Reset code sent to " + req.getEmail() + "."));
    }

    /**
     * Resets the platform password using a reset code.
     *
     * @param req reset password request details
     * @return a Mono containing a success message
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Validates the code received by email and sets the new password.")
    public Mono<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return auth_service.resetPassword(req)
                .thenReturn(Map.of("message", "Password successfully reset. You can now log in."));
    }

    /**
     * Requests API key regeneration.
     * Sends a confirmation OTP via email.
     *
     * @param auth the current authentication context
     * @return a Mono containing a success message
     */
    @PostMapping("/regenerate-token")
    @Operation(summary = "Request API key regeneration", description = "Sends a confirmation OTP via email. Confirm later with /confirm-regenerate.", security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<Map<String, String>> requestApiKeyRegeneration(Authentication auth) {
        Long platform_id = extractPlatformId(auth);
        return auth_service.requestApiKeyRegeneration(platform_id)
                .thenReturn(Map.of("message", "A confirmation code has been sent by email."));
    }

    /**
     * Confirms API key regeneration after OTP verification.
     * Returns the new raw API key.
     *
     * @param req  OTP verification details
     * @param auth the current authentication context
     * @return a Mono containing the new API key
     */
    @PostMapping("/confirm-regenerate")
    @Operation(summary = "Confirm API key regeneration", description = "Validates the OTP and returns the new raw API key (displayed only once).", security = @SecurityRequirement(name = "bearerAuth"))
    public Mono<EmailVerificationResponse> confirmApiKeyRegeneration(
            @RequestBody OtpVerification req,
            Authentication auth) {
        Long platform_id = extractPlatformId(auth);
        return auth_service.confirmApiKeyRegeneration(platform_id, req.getCode());
    }

    /**
     * Internal helper to extract platform ID from authentication context.
     *
     * @param auth the current authentication context
     * @return the extracted platform ID
     * @throws ResponseStatusException if authentication is invalid or missing
     */
    private Long extractPlatformId(Authentication auth) {
        if (auth == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated.");
        }
        Object cred = auth.getCredentials();
        if (cred instanceof Long id)
            return id;
        if (cred instanceof Number n)
            return n.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token.");
    }
}
