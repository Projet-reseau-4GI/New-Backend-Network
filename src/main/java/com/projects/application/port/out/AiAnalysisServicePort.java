package com.projects.application.port.out;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Outbound port — AI document data extraction contract (Gemini or any future AI).
 */
public interface AiAnalysisServicePort {
    Mono<Map<String, String>> extractDocumentData(String rawOcrText);
}
