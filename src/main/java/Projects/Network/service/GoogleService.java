package Projects.Network.service;

import Projects.Network.dto.GoogleAuthResponse;
import Projects.Network.dto.GoogleUserInfo;
import Projects.Network.model.GoogleException;
import Projects.Network.model.User;
import Projects.Network.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleService {

    private final WebClient.Builder webClientBuilder;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    /**
     * Génère l'URL d'autorisation Google OAuth
     * @return URL complète pour rediriger l'utilisateur
     */
    public Mono<String> getAuthorizationUrl() {
        String url = String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?" +
                        "client_id=%s&" +
                        "redirect_uri=%s&" +
                        "response_type=code&" +
                        "scope=openid email profile&" +
                        "access_type=offline&" +
                        "prompt=consent",
                clientId, redirectUri
        );

        log.info("Generated Google OAuth URL");
        return Mono.just(url);
    }

    /**
     * Authentifie l'utilisateur avec le code Google
     * @param code Code d'autorisation Google
     * @return Réponse contenant le JWT et les infos utilisateur
     */
    public Mono<GoogleAuthResponse> authenticateWithGoogle(String code) {
        log.info("Starting Google authentication with code");

        return exchangeCodeForToken(code)
                .doOnSuccess(token -> log.info("Successfully exchanged code for token"))
                .flatMap(this::getUserInfoFromGoogle)
                .doOnSuccess(info -> log.info("Retrieved user info for email: {}", info.getEmail()))
                .flatMap(this::findOrCreateUser)
                .doOnSuccess(user -> log.info("User processed: {}", user.getEmail()))
                .map(this::buildAuthResponse)
                .doOnError(e -> log.error("Error during Google authentication", e))
                .onErrorMap(this::handleAuthenticationError);
    }

    /**
     * Échange le code d'autorisation contre un access token
     * @param code Code d'autorisation
     * @return Access token
     */
    private Mono<String> exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        return webClientBuilder.build()
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    if (!response.has("access_token")) {
                        throw new RuntimeException("No access token in Google response");
                    }
                    return response.get("access_token").asText();
                })
                .doOnError(WebClientResponseException.class, e ->
                        log.error("Google token exchange failed: {} - {}",
                                e.getStatusCode(), e.getResponseBodyAsString())
                );
    }

    /**
     * Récupère les informations utilisateur depuis Google
     * @param accessToken Token d'accès Google
     * @return Informations utilisateur
     */
    private Mono<GoogleUserInfo> getUserInfoFromGoogle(String accessToken) {
        return webClientBuilder.build()
                .get()
                .uri(USERINFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String email = json.has("email") ? json.get("email").asText() : null;
                    String firstName = json.has("given_name") ? json.get("given_name").asText() : "User";
                    String lastName = json.has("family_name") ? json.get("family_name").asText() : "";
                    String googleId = json.has("id") ? json.get("id").asText() : null;
                    String picture = json.has("picture") ? json.get("picture").asText() : null;
                    boolean emailVerified = json.has("verified_email") && json.get("verified_email").asBoolean();

                    if (email == null) {
                        throw new RuntimeException("Email not provided by Google");
                    }

                    GoogleUserInfo userInfo = new GoogleUserInfo();
                    userInfo.setEmail(email);
                    userInfo.setFirstName(firstName);
                    userInfo.setLastName(lastName);
                    userInfo.setGoogleId(googleId);
                    userInfo.setPicture(picture);
                    userInfo.setEmailVerified(emailVerified);

                    return userInfo;
                })
                .doOnError(WebClientResponseException.class, e ->
                        log.error("Failed to get user info from Google: {} - {}",
                                e.getStatusCode(), e.getResponseBodyAsString())
                );
    }

    /**
     * Trouve un utilisateur existant ou en crée un nouveau
     * @param info Informations Google de l'utilisateur
     * @return Entité utilisateur
     */
    private Mono<User> findOrCreateUser(GoogleUserInfo info) {
        return userRepository.findByEmail(info.getEmail())
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("Existing user found: {}", user.getEmail());
                    }
                })
                .switchIfEmpty(Mono.defer(() -> createNewUser(info)));
    }

    /**
     * Crée un nouvel utilisateur depuis les infos Google
     * @param info Informations Google
     * @return Nouvel utilisateur créé
     */
    private Mono<User> createNewUser(GoogleUserInfo info) {
        log.info("Creating new user for email: {}", info.getEmail());

        User newUser = User.builder()
                .userId(UUID.randomUUID())
                .email(info.getEmail())
                .firstName(info.getFirstName())
                .lastName(info.getLastName())
                .password("GOOGLE_OAUTH") // Mot de passe placeholder pour OAuth
                .build();

        return userRepository.save(newUser)
                .doOnSuccess(user -> log.info("New user created with ID: {}", user.getUserId()))
                .doOnError(e -> log.error("Failed to create user", e));
    }

    /**
     * Construit la réponse d'authentification
     * @param user Utilisateur authentifié
     * @return Réponse avec JWT et infos
     */
    private GoogleAuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);

        return GoogleAuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(buildFullName(user.getFirstName(), user.getLastName()))
                .build();
    }

    /**
     * Construit le nom complet
     */
    private String buildFullName(String firstName, String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Valide un token Google (optionnel - pour vérification supplémentaire)
     * @param accessToken Token à valider
     * @return true si valide
     */
    public Mono<Boolean> validateGoogleToken(String accessToken) {
        return webClientBuilder.build()
                .get()
                .uri(TOKEN_INFO_URL + "?access_token=" + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.has("email") && response.has("email_verified"))
                .onErrorReturn(false)
                .doOnSuccess(valid -> log.info("Token validation result: {}", valid));
    }

    /**
     * Révoque un token Google (déconnexion)
     * @param accessToken Token à révoquer
     * @return Confirmation
     */
    public Mono<Void> revokeGoogleToken(String accessToken) {
        return webClientBuilder.build()
                .post()
                .uri("https://oauth2.googleapis.com/revoke?token=" + accessToken)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Google token revoked successfully"))
                .doOnError(e -> log.error("Failed to revoke Google token", e));
    }

    /**
     * Gère les erreurs d'authentification
     */
    private Throwable handleAuthenticationError(Throwable error) {
        if (error instanceof WebClientResponseException webClientError) {
            int statusCode = webClientError.getStatusCode().value();
            String responseBody = webClientError.getResponseBodyAsString();

            log.error("Google API error - Status: {}, Body: {}", statusCode, responseBody);

            return switch (statusCode) {
                case 400 -> new GoogleException("Code d'autorisation invalide ou expiré");
                case 401 -> new GoogleException("Authentification Google échouée");
                case 403 -> new GoogleException("Accès refusé par Google");
                default -> new GoogleException("Erreur lors de l'authentification Google");
            };
        }

        log.error("Unexpected error during Google authentication", error);
        return new GoogleException("Erreur inattendue lors de l'authentification");
    }

    /**
     * Rafraîchit un token Google (si refresh_token disponible)
     * @param refreshToken Refresh token
     * @return Nouveau access token
     */
    public Mono<String> refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        return webClientBuilder.build()
                .post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.get("access_token").asText())
                .doOnSuccess(token -> log.info("Access token refreshed"))
                .doOnError(e -> log.error("Failed to refresh token", e));
    }
}