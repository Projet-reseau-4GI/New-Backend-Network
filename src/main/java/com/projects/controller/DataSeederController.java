package com.projects.controller;

import com.projects.service.DataSeederService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
@Tag(name = "Administration", description = "Outils d'administration et de génération de données de démonstration")
public class DataSeederController {

    private final DataSeederService seederService;

    @PostMapping("/seed-data")
    @Operation(
        summary = "Générer des données de démonstration",
        description = "Génère des vérifications réalistes sur les N derniers jours (défaut: 90). " +
                      "À désactiver en production."
    )
    public Mono<Map<String, String>> seedData(
            @RequestParam(defaultValue = "90") int days) {
        log.info("Seeding {} days of demo data", days);
        return seederService.seedDemoData(days)
            .map(msg -> Map.of("result", msg));
    }
}
