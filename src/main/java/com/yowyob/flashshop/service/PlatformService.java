package com.yowyob.flashshop.service;

import com.yowyob.flashshop.model.Platform;
import com.yowyob.flashshop.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Service for platform administration management.
 *
 * Responsibilities limited to administrative operations:
 * - List all platforms.
 * - Toggle platform active/inactive status.
 *
 * Platform creation and API key management are handled by
 * {@link PlatformAuthService} (secure flow with OTP + password + email
 * verification).
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformService {

    private final PlatformRepository platform_repository;
    private final DatabaseClient database_client;

    /**
     * Returns all registered platforms.
     *
     * @return a Flux of platforms
     */
    public Flux<Platform> getAllPlatforms() {
        return platform_repository.findAll();
    }

    /**
     * Toggles the active/inactive state of a platform.
     * An inactive platform cannot perform verifications.
     *
     * @param platform_id the ID of the platform to toggle
     * @return a Mono with the updated platform
     */
    public Mono<Platform> toggleStatus(Long platform_id) {
        return platform_repository.findById(platform_id)
                .flatMap(platform -> {
                    boolean new_state = !Boolean.TRUE.equals(platform.getActive());
                    platform.setActive(new_state);
                    platform.setUpdatedAt(LocalDateTime.now());
                    log.info("Platform id={} status toggled to active={}", platform_id, new_state);
                    return platform_repository.save(platform);
                })
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform not found.")));
    }

    /**
     * Retrieves all platforms with their API token usage statistics.
     *
     * @param search the search query for filtering platforms
     * @return a Flux of platform token statistics DTOs
     */
    public Flux<com.yowyob.flashshop.dto.PlatformTokenStatsDto> getPlatformTokenStats(String search) {
        String search_filter = "";
        if (search != null && !search.trim().isEmpty()) {
            String safe_search = search.replace("'", "''");
            search_filter = " WHERE p.name ILIKE '%" + safe_search + "%' OR p.email ILIKE '%" + safe_search
                    + "%' OR p.api_key ILIKE '%" + safe_search + "%'";
        }

        String sql = "SELECT p.id, p.name, p.email, p.api_key, p.active, " +
                "       COUNT(vl.id) AS total_calls, " +
                "       MAX(vl.date) AS last_call_date " +
                "FROM platforms p " +
                "LEFT JOIN verification_logs vl ON p.id = vl.platform_id " +
                search_filter +
                " GROUP BY p.id, p.name, p.email, p.api_key, p.active " +
                " ORDER BY p.name ASC";

        return database_client.sql(sql)
                .map((row, md) -> com.yowyob.flashshop.dto.PlatformTokenStatsDto.builder()
                        .platformId(row.get("id", Long.class))
                        .platformName(row.get("name", String.class))
                        .email(row.get("email", String.class))
                        .apiKey(row.get("api_key", String.class))
                        .active(row.get("active", Boolean.class))
                        .totalCalls(row.get("total_calls", Long.class))
                        .lastCallDate(row.get("last_call_date", LocalDateTime.class))
                        .build())
                .all();
    }
}
