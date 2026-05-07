package Projects.Network.service;

import Projects.Network.model.Platform;
import Projects.Network.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Service de gestion admin des plateformes.
 *
 * Responsabilités limitées aux opérations purement admin :
 *  - Lister toutes les plateformes.
 *  - Activer / désactiver une plateforme.
 *
 * La création de plateforme et la gestion des clés API sont désormais
 * entièrement gérées par {@link PlatformAuthService} (flux sécurisé
 * avec OTP + mot de passe + vérification email).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformService {

    private final PlatformRepository platformRepository;

    /**
     * Retourne toutes les plateformes enregistrées.
     */
    public Flux<Platform> getAllPlatforms() {
        return platformRepository.findAll();
    }

    /**
     * Inverse l'état actif/inactif d'une plateforme.
     * Une plateforme inactive ne peut plus effectuer de vérifications.
     */
    public Mono<Platform> toggleStatus(Long platformId) {
        return platformRepository.findById(platformId)
            .flatMap(platform -> {
                boolean newState = !Boolean.TRUE.equals(platform.getActive());
                platform.setActive(newState);
                platform.setUpdatedAt(LocalDateTime.now());
                log.info("Platform id={} status toggled to active={}", platformId, newState);
                return platformRepository.save(platform);
            })
            .switchIfEmpty(Mono.error(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Plateforme introuvable.")));
    }
}
