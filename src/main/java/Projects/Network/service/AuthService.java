package Projects.Network.service;

import Projects.Network.model.User;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service class managing user registration and authentication flow.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Registers a new user and encrypts the password before saving.
     * @param user The user object to persist
     * @return A Mono emitting the saved user
     */
    public Mono<User> register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    /**
     * Authenticates user and returns a token with a success message.
     * @param email User email address
     * @param password Raw password to verify
     * @return A Mono containing the JWT token
     */
    public Mono<String> login(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(jwtService::generateToken)
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials")));
    }
}