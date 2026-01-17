package Projects.Network.controller;

import Projects.Network.dto.GoogleAuthResponse;
import Projects.Network.service.GoogleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private final GoogleService googleAuthService;

    /**
     * Retourne l'URL d'autorisation Google
     * GET /api/auth/google/url
     */
    @GetMapping("/url")
    public Mono<ResponseEntity<AuthUrlResponse>> getAuthUrl() {
        return googleAuthService.getAuthorizationUrl()
                .map(url -> ResponseEntity.ok(new AuthUrlResponse(url)));
    }

    /**
     * Callback Google après authentification
     * POST /api/auth/google/callback
     */
    @PostMapping("/callback")
    public Mono<ResponseEntity<GoogleAuthResponse>> handleCallback(
            @RequestBody GoogleCallbackRequest request) {
        return googleAuthService.authenticateWithGoogle(request.code())
                .map(ResponseEntity::ok)
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.badRequest().build())
                );
    }
    // DTOs
    record AuthUrlResponse(String url) {}
    record GoogleCallbackRequest(String code) {}
}

