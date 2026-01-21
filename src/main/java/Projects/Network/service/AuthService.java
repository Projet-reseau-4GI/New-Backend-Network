package Projects.Network.service;

import Projects.Network.model.User;
import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordValidationService passwordValidationService;

    public Mono<User> register(User user) {
        // Validation du mot de passe
        PasswordValidationService.ValidationResult validationResult = 
            passwordValidationService.validate(user.getPassword());
        
        if (!validationResult.isValid()) {
            return Mono.error(new IllegalArgumentException(validationResult.getErrorMessage()));
        }
        
        // Vérifier si l'email existe déjà
        return userRepository.findByEmail(user.getEmail())
            .flatMap(existingUser -> 
                Mono.<User>error(new RuntimeException("Cet email est déjà utilisé"))
            )
            .switchIfEmpty(Mono.defer(() -> {
                // Encoder le mot de passe
                String encoded = passwordEncoder.encode(user.getPassword());
                user.setPassword(encoded);
                
                // Sauvegarder l'utilisateur
                return userRepository.save(user);
            }));
    }

    public Mono<String> login(String email, String password) {
        return userRepository.findByEmail(email)
            .filter(user -> passwordEncoder.matches(password, user.getPassword()))
            .map(jwtService::generateToken)
            .switchIfEmpty(Mono.error(new RuntimeException("Invalid credentials")));
    }
}