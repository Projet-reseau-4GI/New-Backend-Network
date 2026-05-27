package com.yowyob.flashshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for interacting with Google's Gemini AI API.
 * Used for extracting structured identity data from raw OCR text.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@Slf4j
public class GeminiService {

    private final WebClient web_client;
    private final String api_url;
    private final ObjectMapper object_mapper = new ObjectMapper();

    /**
     * Initializes the Gemini service with API configuration.
     *
     * @param web_client_builder the builder used to create the WebClient instance
     * @param api_key            the API key for Gemini access
     * @param model              the Gemini model name (e.g., gemini-1.5-flash)
     * @param api_url_template   the template URL for the API endpoint
     */
    public GeminiService(WebClient.Builder web_client_builder,
            @Value("${gemini.api.key}") String api_key,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.url}") String api_url_template) {

        this.web_client = web_client_builder.build();
        this.api_url = api_url_template
                .replace("{model}", model)
                .replace("{key}", api_key);
    }

    /**
     * Extracts structured identity data from raw OCR text using Gemini.
     *
     * @param raw_text the raw text extracted from the document
     * @return a Mono containing a map of extracted data fields
     */
    public Mono<Map<String, String>> extractData(String raw_text) {
        if (raw_text == null || raw_text.isBlank()) {
            return Mono.just(new HashMap<>());
        }

        String prompt = """
                You are a strict OCR identity extraction engine for CEMAC zone documents (Cameroon, Chad, Congo, DRC, Gabon, Central African Republic).

                RULES:
                - Return ONLY valid flat JSON.
                - Use null for missing fields.
                - Never return labels as values.
                - Dates format: yyyy-MM-dd.
                - documentType must be exactly: ID_CARD, PASSPORT, DRIVER_LICENSE.

                Return a single JSON object with EXACTLY these keys:
                {
                  "documentType": "...",
                  "issuingCountry": "...",
                  "surname": "...",
                  "givenNames": "...",
                  "dateOfBirth": "...",
                  "issueDate": "...",
                  "expiryDate": "...",
                  "documentNumber": "...",
                  "sex": "...",
                  "height": "...",
                  "placeOfBirth": "...",
                  "occupation": "..."
                }

                Raw OCR text:
                """
                + raw_text;

        Map<String, Object> request_body = Map.of(
                "generationConfig", Map.of("responseMimeType", "application/json"),
                "contents", new Object[] {
                        Map.of("role", "user",
                                "parts", new Object[] {
                                        Map.of("text", prompt)
                                })
                });

        return web_client.post()
                .uri(api_url)
                .header("Content-Type", "application/json")
                .bodyValue(request_body)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseGeminiResponse)
                .doOnError(e -> log.error("Gemini API error: {}", e.getMessage()))
                .onErrorReturn(new HashMap<>());
    }

    private Map<String, String> parseGeminiResponse(String response) {
        Map<String, String> result = new HashMap<>();
        try {
            JsonNode root = object_mapper.readTree(response);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            // Clean up possible markdown blocks
            text = text.replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            JsonNode json = object_mapper.readTree(text);
            json.fields().forEachRemaining(e -> {
                if (!e.getValue().isNull()) {
                    result.put(e.getKey(), e.getValue().asText());
                }
            });

        } catch (Exception e) {
            log.warn("Failed to parse Gemini response: {}", e.getMessage());
        }
        return result;
    }
}
