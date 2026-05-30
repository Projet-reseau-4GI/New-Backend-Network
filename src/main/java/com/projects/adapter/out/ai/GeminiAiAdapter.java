package com.projects.adapter.out.ai;

import com.projects.application.port.out.AiAnalysisServicePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure adapter — implements AiAnalysisServicePort using Google Gemini API.
 */
@Component
@Slf4j
public class GeminiAiAdapter implements AiAnalysisServicePort {

    private final WebClient webClient;
    private final String apiUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAiAdapter(WebClient.Builder webClientBuilder,
                           @Value("${gemini.api.key}") String apiKey,
                           @Value("${gemini.model}") String model,
                           @Value("${gemini.api.url}") String apiUrlTemplate) {
        this.webClient = webClientBuilder.build();
        this.apiUrl = apiUrlTemplate.replace("{model}", model).replace("{key}", apiKey);
    }

    @Override
    public Mono<Map<String, String>> extractDocumentData(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.isBlank()) return Mono.just(new HashMap<>());

        String prompt = """
                You are a strict OCR identity extraction engine for CEMAC zone documents.

                RULES:
                - Return ONLY valid flat JSON.
                - Use null for missing fields.
                - Dates format: yyyy-MM-dd.
                - documentType must be exactly: ID_CARD, PASSPORT, DRIVER_LICENSE.

                Return a single JSON object with EXACTLY these keys:
                {
                  "documentType": "...", "issuingCountry": "...", "surname": "...",
                  "givenNames": "...", "dateOfBirth": "...", "issueDate": "...",
                  "expiryDate": "...", "documentNumber": "...", "sex": "...",
                  "height": "...", "placeOfBirth": "...", "occupation": "..."
                }

                Raw OCR text:
                """ + rawOcrText;

        Map<String, Object> requestBody = Map.of(
            "generationConfig", Map.of("responseMimeType", "application/json"),
            "contents", new Object[]{Map.of("role", "user",
                "parts", new Object[]{Map.of("text", prompt)})});

        return webClient.post().uri(apiUrl)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .map(this::parseGeminiResponse)
            .doOnError(e -> log.error("Gemini API error: {}", e.getMessage()))
            .onErrorReturn(new HashMap<>());
    }

    private Map<String, String> parseGeminiResponse(String response) {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0)
                .path("content").path("parts").get(0).path("text").asText();
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
            objectMapper.readTree(text).fields()
                .forEachRemaining(e -> { if (!e.getValue().isNull()) result.put(e.getKey(), e.getValue().asText()); });
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
        }
        return result;
    }
}
