package Projects.Network.service;

import Projects.Network.dto.PlatformDto;
import Projects.Network.dto.PlatformResponse;
import Projects.Network.model.Platform;
import Projects.Network.repository.PlatformRepository;
import Projects.Network.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformService {
    
    private final PlatformRepository platformRepository;

    public Flux<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    public Mono<PlatformResponse> createPlatform(PlatformDto dto) {
        String rawApiKey = UUID.randomUUID().toString();
        String hashedApiKey = SecurityUtils.hashApiKey(rawApiKey);
        
        Platform platform = Platform.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .apiKey(hashedApiKey)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return platformRepository.save(platform)
                .map(saved -> mapToResponse(saved, rawApiKey));
    }

    public Mono<PlatformResponse> generateNewApiKey(Long platformId) {
        return platformRepository.findById(platformId)
                .flatMap(platform -> {
                    String rawApiKey = UUID.randomUUID().toString();
                    platform.setApiKey(SecurityUtils.hashApiKey(rawApiKey));
                    platform.setUpdatedAt(LocalDateTime.now());
                    return platformRepository.save(platform)
                            .map(saved -> mapToResponse(saved, rawApiKey));
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform not found")));
    }

    public Mono<Platform> toggleStatus(Long platformId) {
        return platformRepository.findById(platformId)
                .flatMap(platform -> {
                    platform.setActive(!Boolean.TRUE.equals(platform.getActive()));
                    platform.setUpdatedAt(LocalDateTime.now());
                    return platformRepository.save(platform);
                })
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform not found")));
    }

    private PlatformResponse mapToResponse(Platform platform, String rawApiKey) {
        return PlatformResponse.builder()
                .id(platform.getId())
                .name(platform.getName())
                .email(platform.getEmail())
                .apiKey(rawApiKey)
                .active(platform.getActive())
                .createdAt(platform.getCreatedAt())
                .updatedAt(platform.getUpdatedAt())
                .build();
    }
}
