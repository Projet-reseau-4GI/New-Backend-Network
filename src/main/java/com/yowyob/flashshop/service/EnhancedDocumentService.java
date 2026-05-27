package com.yowyob.flashshop.service;

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
 * Service for handling OCR operations to extract text from document images or
 * PDFs.
 * Interfaces with external parsing APIs.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@Slf4j
public class EnhancedDocumentService {

    private final WebClient web_client;

    @Value("${parsing.api.url}")
    private String parsing_api_url;

    @Value("${parsing.api.token}")
    private String parsing_api_token;

    /**
     * Initializes the enhanced document service.
     *
     * @param web_client_builder the builder used to create the WebClient instance
     */
    @Autowired
    public EnhancedDocumentService(WebClient.Builder web_client_builder) {
        this.web_client = web_client_builder.build();
    }

    /**
     * Performs in-memory OCR on document bytes.
     *
     * @param content bytes of the document to be processed
     * @param is_pdf  true if the document is a PDF, false otherwise
     * @return a Mono containing the extracted markdown text
     */
    public Mono<String> extractMarkdownFromBytes(byte[] content, boolean is_pdf) {
        log.info("Performing in-memory OCR. Content size: {} bytes", content.length);
        String base64_file = Base64.getEncoder().encodeToString(content);

        Map<String, Object> payload = new HashMap<>();
        payload.put("file", base64_file);
        payload.put("fileType", is_pdf ? 0 : 1);

        return web_client.post()
                .uri(parsing_api_url)
                .header("Authorization", "token " + parsing_api_token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("OCR API Error {}: {}", response.statusCode(), body);
                            return Mono.error(new RuntimeException(
                                    "OCR API returned " + response.statusCode() + ": " + body));
                        }))
                .bodyToMono(Map.class)
                .timeout(java.time.Duration.ofSeconds(300))
                .map(response -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> result = (Map<String, Object>) response.get("result");
                        java.util.List<?> layouts = (java.util.List<?>) result.get("layoutParsingResults");
                        if (layouts == null || layouts.isEmpty()) {
                            return "";
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> first = (Map<String, Object>) layouts.get(0);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> markdown = (Map<String, Object>) first.get("markdown");
                        return (String) markdown.get("text");
                    } catch (Exception e) {
                        log.error("Failed to parse OCR response: {}", e.getMessage());
                        return "";
                    }
                });
    }
}