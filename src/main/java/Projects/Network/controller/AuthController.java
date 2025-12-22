package Projects.Network.controller;

import Projects.Network.dto.AuthResponse;
import Projects.Network.model.User;
import Projects.Network.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controller handling authentication requests.
 * Complies with the development charter by separating logic from data transfer.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint to register a new user.
     * @param user The user data (userId will be ignored if sent)
     * @return Success message string
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody User user) {
        return authService.register(user)
                .map(u -> ResponseEntity.status(HttpStatus.CREATED)
                        .body("User registered successfully"));
    }

    /**
     * Endpoint for login.
     * @param loginRequest User object containing email and password
     * @return AuthResponse containing the token and success message
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody User loginRequest) {
        // Ensure email and password are provided to avoid 400 error logic
        return authService.login(loginRequest.getEmail(), loginRequest.getPassword())
                .map(token -> ResponseEntity.ok(
                        new AuthResponse("Authentication successful", token)
                ));
    }
}