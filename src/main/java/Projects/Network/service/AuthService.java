package Projects.Network.service;

import Projects.Network.model.User;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * AuthService
 *
 * Service responsible for handling authentication-related operations
 * such as user registration and login.
 *
 * This class belongs to the Service layer and contains business logic
 * related to authentication workflows.
 *
 * It interacts with the UserRepository for persistence operations,
 * uses a PasswordEncoder for password hashing, and relies on JwtService
 * to generate JSON Web Tokens after successful authentication.
 *
 * This service follows a reactive programming model using Project Reactor.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Repository used to persist and retrieve User entities.
     */
    private final UserRepository userRepository;

    /**
     * Component responsible for hashing and verifying user passwords.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Service used to generate JWT tokens for authenticated users.
     */
    private final JwtService jwtService;

    /**
     * Registers a new user in the system.
     *
     * This method encodes the user's raw password before saving the user
     * entity into the database.
     *
     * The operation is performed in a reactive and non-blocking manner.
     *
     * Note: Console logs are present for debugging and educational purposes.
     * In a production environment, sensitive information such as raw passwords
     * should never be logged.
     *
     * @param user the User entity containing registration information
     * @return a Mono emitting the saved User entity
     */
    public Mono<User> register(User user) {
        try {
            System.out.println("=== REGISTER START ===");
            System.out.println("Email: " + user.getEmail());
            System.out.println("Password (raw): " + user.getPassword());

            // Encode the raw password before persisting the user
            String encoded = passwordEncoder.encode(user.getPassword());
            System.out.println("Password (encoded): " + encoded);

            user.setPassword(encoded);

            return userRepository.save(user)
                    .doOnSuccess(u ->
                            System.out.println("User saved: " + u.getUserId())
                    )
                    .doOnError(e -> {
                        System.err.println("ERROR saving user: " + e.getMessage());
                        e.printStackTrace();
                    });
        } catch (Exception e) {
            System.err.println("ERROR in register: " + e.getMessage());
            e.printStackTrace();
            return Mono.error(e);
        }
    }

    /**
     * Authenticates a user using email and password.
     *
     * This method retrieves the user by email, verifies the provided
     * password against the stored encoded password, and generates
     * a JWT token if authentication is successful.
     *
     * If the credentials are invalid, an error is propagated downstream.
     *
     * @param email the user's email address
     * @param password the raw password provided by the user
     * @return a Mono emitting a JWT token if authentication succeeds
     */
    public Mono<String> login(String email, String password) {
        System.out.println("=== LOGIN START ===");
        System.out.println("Email: " + email);

        return userRepository.findByEmail(email)
                .doOnNext(user ->
                        System.out.println("User found: " + user.getEmail())
                )
                .filter(user -> {
                    boolean matches = passwordEncoder.matches(password, user.getPassword());
                    System.out.println("Password matches: " + matches);
                    return matches;
                })
                .map(jwtService::generateToken)
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials")))
                .doOnError(e ->
                        System.err.println("Login error: " + e.getMessage())
                );
    }
}
