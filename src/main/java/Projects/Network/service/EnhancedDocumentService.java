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
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", Base64.getEncoder().encodeToString(decrypted));
                    payload.put("fileType", objectName.toLowerCase().endsWith(".pdf") ? 0 : 1);

                    return webClient.post()
                            .uri(parsingApiUrl)
                            .header("Authorization", "token " + parsingApiToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(response -> {
                                var result = (Map<String, Object>) response.get("result");
                                var layouts = (java.util.List<?>) result.get("layoutParsingResults");
                                var first = (Map<String, Object>) layouts.get(0);
                                var markdown = (Map<String, Object>) first.get("markdown");
                                return (String) markdown.get("text");

    public Mono<byte[]> retrieveFileFromSupabase(String objectName) {
        return supabaseStorageService.downloadAndDecryptFile(objectName);
    }
}