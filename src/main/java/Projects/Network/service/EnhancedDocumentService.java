package Projects.Network.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EnhancedDocumentService {

    private final SupabaseStorageService supabaseStorageService;
    private final WebClient webClient;

    @Autowired
    public EnhancedDocumentService(SupabaseStorageService supabaseStorageService, WebClient.Builder webClientBuilder) {
        this.supabaseStorageService = supabaseStorageService;
        this.webClient = webClientBuilder.build();
    }

    @Value("${parsing.api.url}")
    private String parsingApiUrl;

    @Value("${parsing.api.token}")
    private String parsingApiToken;

    public Mono<String> extractMarkdownText(String objectName) {
        return supabaseStorageService.downloadAndDecryptFile(objectName)
                .flatMap(decrypted -> {
                    log.info("PDF/Image detected. Decrypted size: {} bytes", decrypted.length);
                    String base64File = Base64.getEncoder().encodeToString(decrypted);
                    log.info("Base64 payload size: {} characters", base64File.length());

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", base64File);
                    payload.put("fileType", objectName.toLowerCase().endsWith(".pdf") ? 0 : 1);

                    log.debug("Calling OCR API with fileType: {} for {}", payload.get("fileType"), objectName);

                    return webClient.post()
                            .uri(parsingApiUrl)
                            .header("Authorization", "token " + parsingApiToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload)
                            .retrieve()
                            .onStatus(status -> status.isError(), response -> {
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("OCR API Error {}: {}", response.statusCode(), body);
                                            return Mono.error(new RuntimeException(
                                                    "OCR API returned " + response.statusCode() + ": " + body));
                                        });
                            })
                            .bodyToMono(Map.class)
                            .timeout(java.time.Duration.ofSeconds(300))
                            .map(response -> {
                                try {
                                    var result = (Map<String, Object>) response.get("result");
                                    var layouts = (java.util.List<?>) result.get("layoutParsingResults");
                                    if (layouts == null || layouts.isEmpty()) {
                                        log.warn("OCR API returned empty results for {}", objectName);
                                        return "";
                                    }
                                    var first = (Map<String, Object>) layouts.get(0);
                                    var markdown = (Map<String, Object>) first.get("markdown");
                                    return (String) markdown.get("text");
                                } catch (Exception e) {
                                    log.error("Failed to parse OCR response: {}", e.getMessage());
                                    return "";
                                }
                            })
                            .doOnError(
                                    e -> log.error("Error calling parsing API for {}: {}", objectName, e.getMessage()));
                })
                .doOnSuccess(text -> log.info("Successfully extracted {} characters from {}",
                        text != null ? text.length() : 0, objectName))
                .doOnError(e -> log.error("Failed to extract markdown from {}: {}", objectName, e.getMessage()));

    }

    public Mono<byte[]> retrieveFileFromSupabase(String objectName) {
        return supabaseStorageService.downloadAndDecryptFile(objectName);
    }
}