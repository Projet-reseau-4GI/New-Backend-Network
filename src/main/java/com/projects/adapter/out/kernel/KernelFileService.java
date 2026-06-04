package com.projects.adapter.out.kernel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur HTTP vers le file-core du Kernel RT-Comops.
 *
 * Endpoints utilisés :
 *   POST /api/files        → upload d'un fichier (multipart)
 *   GET  /api/files/{id}   → téléchargement d'un fichier
 *
 * Headers kernel requis (automatiquement injectés par le kernelWebClient) :
 *   X-Client-Id, X-Api-Key
 *
 * Headers contextuels (passés en paramètre selon la requête entrante) :
 *   X-Tenant-Id, X-Organization-Id, Authorization
 */
@Service
@Slf4j
public class KernelFileService {

    private static final String FILES_ENDPOINT = "/api/files";

    private final WebClient kernelWebClient;

    public KernelFileService(@Qualifier("kernelWebClient") WebClient kernelWebClient) {
        this.kernelWebClient = kernelWebClient;
    }

    /**
     * Envoie un fichier au file-core du kernel et retourne les métadonnées du fichier stocké.
     *
     * @param fileName    Nom du fichier
     * @param contentType MIME type (ex: image/jpeg, application/pdf)
     * @param content     Flux de DataBuffers du contenu du fichier
     * @param tenantId    UUID du tenant (X-Tenant-Id)
     * @param orgId       UUID de l'organisation (X-Organization-Id)
     * @param bearerToken JWT RS256 de l'utilisateur (Authorization: Bearer)
     * @return Métadonnées du fichier stocké (StoredFileResponse)
     */
    public Mono<StoredFileResponse> storeFile(
            String fileName,
            String contentType,
            Flux<DataBuffer> content,
            UUID tenantId,
            UUID orgId,
            String bearerToken) {

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.asyncPart("file", content, DataBuffer.class)
                .headers(h -> {
                    h.setContentType(MediaType.parseMediaType(contentType));
                    h.setContentDispositionFormData("file", fileName);
                });

        return kernelWebClient.post()
                .uri(FILES_ENDPOINT)
                .headers(h -> applyKernelHeaders(h, tenantId, orgId, bearerToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(KernelApiResponse.class)
                .map(response -> parseStoredFile(response.data()))
                .doOnSuccess(f -> log.info("[file-core] Fichier stocké — id={} tenant={}", f.id(), tenantId))
                .doOnError(e -> log.error("[file-core] Erreur upload fichier : {}", e.getMessage()));
    }

    /**
     * Récupère les métadonnées d'un fichier depuis le file-core.
     */
    public Mono<StoredFileResponse> getFileMetadata(UUID fileId, UUID tenantId, UUID orgId, String bearerToken) {
        return kernelWebClient.get()
                .uri(FILES_ENDPOINT + "/" + fileId)
                .headers(h -> applyKernelHeaders(h, tenantId, orgId, bearerToken))
                .retrieve()
                .bodyToMono(StoredFileResponse.class)
                .doOnError(e -> log.error("[file-core] Erreur récupération fichier {} : {}", fileId, e.getMessage()));
    }

    // ----------------------------------------------------------------
    // Types de réponse alignés sur FileController du kernel
    // ----------------------------------------------------------------

    public record StoredFileResponse(
            UUID id,
            UUID organizationId,
            UUID uploadedByUserId,
            String fileName,
            String contentType,
            long size
    ) {}

    @SuppressWarnings("unchecked")
    private StoredFileResponse parseStoredFile(Object data) {
        if (data instanceof Map<?, ?> map) {
            return new StoredFileResponse(
                    parseUuid(map, "id"),
                    parseUuid(map, "organizationId"),
                    parseUuid(map, "uploadedByUserId"),
                    (String) map.get("fileName"),
                    (String) map.get("contentType"),
                    map.get("size") instanceof Number n ? n.longValue() : 0L
            );
        }
        throw new IllegalStateException("Réponse file-core inattendue");
    }

    private UUID parseUuid(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null ? UUID.fromString(val.toString()) : null;
    }

    private void applyKernelHeaders(HttpHeaders headers, UUID tenantId, UUID orgId, String bearerToken) {
        if (tenantId != null) {
            headers.set("X-Tenant-Id", tenantId.toString());
        }
        if (orgId != null) {
            headers.set("X-Organization-Id", orgId.toString());
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
    }

    /** Wrapper générique pour les réponses ApiResponse<T> du kernel */
    private record KernelApiResponse(boolean success, String message, Object data) {}
}
