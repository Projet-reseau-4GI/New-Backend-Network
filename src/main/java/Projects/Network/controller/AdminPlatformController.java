package Projects.Network.controller;

import Projects.Network.dto.PlatformDto;
import Projects.Network.dto.PlatformResponse;
import Projects.Network.model.Platform;
import Projects.Network.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/platforms")
@RequiredArgsConstructor
public class AdminPlatformController {

    private final PlatformService platformService;

    @GetMapping
    public Flux<Platform> getAllPlatforms() {
        return platformService.getAllPlatforms();
    }

    @PostMapping
    public Mono<PlatformResponse> createPlatform(@RequestBody PlatformDto dto) {
        return platformService.createPlatform(dto);
    }

    @PostMapping("/{id}/generate-key")
    public Mono<PlatformResponse> generateNewApiKey(@PathVariable Long id) {
        return platformService.generateNewApiKey(id);
    }

    @PostMapping("/{id}/toggle-status")
    public Mono<Platform> toggleStatus(@PathVariable Long id) {
        return platformService.toggleStatus(id);
    }
}
