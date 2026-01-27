package Projects.Network.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * SupabaseStorageService - Service de gestion du stockage Supabase
 *
 * Gère les opérations de stockage Supabase avec support pour:
 * - Upload de bytes bruts
 * - Upload réactif de FilePart (WebFlux multipart)
 * - Génération d'URLs publiques
 *
 * @author Thomas Djotio Ndié
 * @version 2.1
 */
@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    private final WebClient webClient = WebClient.builder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                    .build())
            .build();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.bucket}")
    private String bucket;

    /**
     * Upload de contenu brut vers Supabase Storage
     *
     * @param objectPath  chemin de destination dans le bucket
     * @param content     contenu binaire à uploader
     * @param contentType type MIME du fichier
     * @return Mono<Void> complété une fois l'upload terminé
     */
    public Mono<Void> uploadFile(String objectPath, byte[] content, String contentType) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        return webClient.put()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .contentType(MediaType.parseMediaType(contentType))
                .bodyValue(content)
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

    /**
     * Upload d'un FilePart réactif vers Supabase Storage
     *
     * @param filePart   fichier multipart réactif depuis WebFlux
     * @param objectPath chemin de destination dans le bucket Supabase
     * @return Mono<String> chemin du fichier uploadé
     */
    public Mono<String> uploadFile(FilePart filePart, String objectPath) {
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        // Détection du content type
                        String contentType = detectContentType(filePart.filename());

                        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

                        return webClient.put()
                                .uri(url)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                                .header("apikey", serviceRoleKey)
                                .contentType(MediaType.parseMediaType(contentType))
                                .bodyValue(bytes)
                                .retrieve()
                                .bodyToMono(String.class)
                                .thenReturn(objectPath);

                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Supabase upload failed: " + e.getMessage(), e));
                    }
                });
    }

    /**
     * Génère l'URL publique d'un fichier stocké
     *
     * @param objectPath chemin du fichier dans le bucket
     * @return URL publique du fichier
     */
    public String getPublicUrl(String objectPath) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    /**
     * Supprime un fichier dans Supabase Storage
     *
     * @param objectPath chemin du fichier dans le bucket
     * @return Mono<Void> complété une fois la suppression terminée
     */
    public Mono<Void> deleteFile(String objectPath) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        return webClient.delete()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .bodyToMono(Void.class);
    }

    /**
     * Télécharge un fichier depuis Supabase Storage
     *
     * @param objectPath chemin du fichier dans le bucket
     * @return Mono<byte[]> contenu binaire du fichier
     */
    public Mono<byte[]> downloadFile(String objectPath) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath;

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    /**
     * Détecte le type MIME depuis le nom de fichier
     *
     * @param filename nom du fichier
     * @return type MIME détecté
     */
    private String detectContentType(String filename) {
        if (filename == null)
            return "application/octet-stream";

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".bmp"))
            return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif"))
            return "image/tiff";
        if (lower.endsWith(".webp"))
            return "image/webp";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";

        return "application/octet-stream";
    }
}