package Projects.Network.controller;

import Projects.Network.model.User;
import Projects.Network.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_KEY = "token";
    private static final String MESSAGE_KEY = "message";
    private static final String ERROR_KEY = "error";
    private static final String DETAILS_KEY = "details";
    private static final String EMAIL_KEY = "email";
    private static final String USER_ID_KEY = "userId";

    private static final String LOGIN_SUCCESS_MESSAGE = "Login successful";
    private static final String INVALID_CREDENTIALS_ERROR = "Invalid credentials";
    private static final String REGISTRATION_SUCCESS_MESSAGE = "User registered successfully";
    private static final String REGISTRATION_FAILED_ERROR = "Registration failed";

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword())
                .map(token -> {
                    Map<String, String> response = new HashMap<>();
                    response.put(TOKEN_KEY, token);
                    response.put(MESSAGE_KEY, LOGIN_SUCCESS_MESSAGE);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    Map<String, String> error = new HashMap<>();
                    error.put(ERROR_KEY, INVALID_CREDENTIALS_ERROR);
                    error.put(DETAILS_KEY, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
                });
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, String>>> register(@Valid @RequestBody RegisterRequest request) {
        User user = createUserFromRequest(request);

        return authService.register(user)
                .map(savedUser -> {
                    Map<String, String> response = new HashMap<>();
                    response.put(MESSAGE_KEY, REGISTRATION_SUCCESS_MESSAGE);
                    response.put(EMAIL_KEY, savedUser.getEmail());
                    response.put(USER_ID_KEY, savedUser.getUserId().toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    Map<String, String> error = new HashMap<>();
                    error.put(ERROR_KEY, REGISTRATION_FAILED_ERROR);
                    error.put(DETAILS_KEY, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
                });
    }

    private User createUserFromRequest(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        return user;
    }

    /**
     * DTO pour les requêtes de connexion
     */
    @Data
    public static class LoginRequest {
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        private String email;

        @NotBlank(message = "Le mot de passe est requis")
        private String password;
    }

    /**
     * DTO pour les requêtes d'inscription avec validation stricte du mot de passe
     */
    @Data
    public static class RegisterRequest {
        @NotBlank(message = "L'email est requis")
        @Email(message = "Format d'email invalide")
        @Size(max = 255, message = "L'email ne peut pas dépasser 255 caractères")
        private String email;

        @NotBlank(message = "Le mot de passe est requis")
        @Size(min = 8, max = 128, message = "Le mot de passe doit contenir entre 8 et 128 caractères")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Le mot de passe doit contenir au moins : une majuscule, une minuscule, un chiffre et un caractère spécial (@$!%*?&)"
        )
        private String password;

        @NotBlank(message = "Le prénom est requis")
        @Size(min = 1, max = 100, message = "Le prénom doit contenir entre 1 et 100 caractères")
        private String firstName;

        @NotBlank(message = "Le nom est requis")
        @Size(min = 1, max = 100, message = "Le nom doit contenir entre 1 et 100 caractères")
        private String lastName;
    }
}