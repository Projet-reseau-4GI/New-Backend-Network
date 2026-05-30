package com.projects.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Outbound port — OCR text extraction from document images/PDFs.
 */
public interface OcrServicePort {
    Mono<String> extractText(byte[] content, boolean isPdf);
}
