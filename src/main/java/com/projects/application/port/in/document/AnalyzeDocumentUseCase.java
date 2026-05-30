package com.projects.application.port.in.document;

import com.projects.adapter.in.web.dto.DocumentAnalysisResponse;
import reactor.core.publisher.Mono;

/**
 * Inbound port — Document analysis use case.
 */
public interface AnalyzeDocumentUseCase {
    Mono<DocumentAnalysisResponse> analyzeDocument(byte[] frontBytes, byte[] backBytes,
                                                    String frontFilename, Long platformId);
}
