package Projects.Network.controller;

import Projects.Network.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/find-id")
    public Mono<ResponseEntity<Map<String, UUID>>> getMemberIdByEmail(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(user -> ResponseEntity.ok(Map.of("userId", user.getUserId())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
