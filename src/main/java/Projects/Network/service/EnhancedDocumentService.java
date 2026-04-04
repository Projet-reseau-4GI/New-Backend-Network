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

/**
 * EnhancedDocumentService handles OCR operations for extracting text from document images/PDFs.
 */
@Service
@Slf4j
public class EnhancedDocumentService {

    private final WebClient webClient;

    @Autowired
    public EnhancedDocumentService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Value("${parsing.api.url}")
    private String parsingApiUrl;

    @Value("${parsing.api.token}")
    private String parsingApiToken;

    /**
     * Performs in-memory OCR on document bytes.
     * @param content bytes of the document
     * @param isPdf true if the document is a PDF
     * @return Mono of extracted markdown text
     */
    public Mono<String> extractMarkdownFromBytes(byte[] content, boolean isPdf) {
        log.info("Performing in-memory OCR. Content size: {} bytes", content.length);
        String base64File = Base64.getEncoder().encodeToString(content);

        Map<String, Object> payload = new HashMap<>();
        payload.put("file", base64File);
        payload.put("fileType", isPdf ? 0 : 1);

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
                            return "";
                        }
                        var first = (Map<String, Object>) layouts.get(0);
                        var markdown = (Map<String, Object>) first.get("markdown");
                        return (String) markdown.get("text");
                    } catch (Exception e) {
                        log.error("Failed to parse OCR response: {}", e.getMessage());
                        return "";
                    }
                });
    }
}