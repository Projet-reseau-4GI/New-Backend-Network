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

    @Data
    static class EmailRequest {
        private String email;
    }

    @GetMapping("/find-id")
    public Mono<ResponseEntity<Map<String, UUID>>> getMemberIdByEmail(@RequestBody EmailRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .map(user -> ResponseEntity.ok(Map.of("userId", user.getUserId())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
