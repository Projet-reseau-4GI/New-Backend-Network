package com.yowyob.flashshop.controller;

import com.yowyob.flashshop.dto.DocumentAnalysisResponse;
import com.yowyob.flashshop.model.DocumentEntity;
import com.yowyob.flashshop.config.ReactiveTenantContext;
import com.yowyob.flashshop.repository.DocumentRepository;
import com.yowyob.flashshop.service.DocumentAnalysisService;
import com.yowyob.flashshop.service.EnhancedDocumentService;
import com.yowyob.flashshop.service.VerificationLoggingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST Controller for document analysis.
 * Provides endpoints for uploading and analyzing identity documents.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DocumentAnalysisController {

        private final DocumentAnalysisService analysis_service;
        private final DocumentRepository document_repository;
        private final VerificationLoggingService logging_service;
        private final EnhancedDocumentService enhanced_document_service;

        /**
         * Uploads and analyzes a document.
         *
         * @param apiKey          The platform's API Key
         * @param front_file_mono Front side of the document
         * @param back_file_mono  Back side of the document (optional)
         * @return a Mono containing the analysis results
         */
        @PostMapping(value = "/upload-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Mono<DocumentAnalysisResponse> uploadAndAnalyze(
                        @RequestHeader("X-API-KEY") String apiKey,
                        @RequestPart("frontFile") Mono<FilePart> front_file_mono,
                        @RequestPart(value = "backFile", required = false) Mono<FilePart> back_file_mono) {

                return ReactiveTenantContext.getPlatform()
                                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found/Invalid API Key")))
                                .flatMap(platform -> front_file_mono.flatMap(front_file -> {
                                        log.info("Starting analysis for platform {} with file {}", platform.getId(),
                                                        front_file.filename());

                                        Mono<byte[]> front_bytes_mono = DataBufferUtils.join(front_file.content())
                                                        .map(db -> {
                                                                byte[] bytes = new byte[db.readableByteCount()];
                                                                db.read(bytes);
                                                                DataBufferUtils.release(db);
                                                                return bytes;
                                                        });

                                        Mono<byte[]> back_bytes_mono = back_file_mono
                                                        .ofType(FilePart.class)
                                                        .flatMap(bf -> DataBufferUtils.join(bf.content())
                                                                        .map(db -> {
                                                                                byte[] bytes = new byte[db
                                                                                                .readableByteCount()];
                                                                                db.read(bytes);
                                                                                DataBufferUtils.release(db);
                                                                                return bytes;
                                                                        }))
                                                        .defaultIfEmpty(new byte[0]);

                                        return Mono.zip(front_bytes_mono, back_bytes_mono)
                                                        .flatMap(bytes_tuple -> {
                                                                byte[] front_bytes = bytes_tuple.getT1();
                                                                byte[] back_bytes = bytes_tuple.getT2();

                                                                boolean is_pdf = front_file.filename().toLowerCase()
                                                                                .endsWith(".pdf");

                                                                Mono<String> front_ocr = enhanced_document_service
                                                                                .extractMarkdownFromBytes(front_bytes,
                                                                                                is_pdf);
                                                                Mono<String> back_ocr = back_bytes.length > 0
                                                                                ? enhanced_document_service
                                                                                                .extractMarkdownFromBytes(
                                                                                                                back_bytes,
                                                                                                                is_pdf)
                                                                                : Mono.just("");

                                                                return Mono.zip(front_ocr, back_ocr)
                                                                                .flatMap(ocr_tuple -> analysis_service
                                                                                                .analyzeFull(ocr_tuple
                                                                                                                .getT1(),
                                                                                                                ocr_tuple.getT2()))
                                                                                .flatMap(analysis -> {
                                                                                        String final_type = analysis
                                                                                                        .getDocumentType();

                                                                                        DocumentEntity doc = DocumentEntity
                                                                                                        .builder()
                                                                                                        .platform_id(platform
                                                                                                                        .getId())
                                                                                                        .piece_type(final_type)
                                                                                                        .status("VERIFIED")
                                                                                                        .upload_date(java.time.LocalDateTime
                                                                                                                        .now())
                                                                                                        .build();

                                                                                        log.info("Saving document record for platform {}",
                                                                                                        platform.getId());

                                                                                        String status = analysis
                                                                                                        .getIsValid() ? "ACCEPTED"
                                                                                                                        : "REJECTED";
                                                                                        String reason = analysis
                                                                                                        .getIsValid() ? null
                                                                                                                        : analysis.getValidationMessage();

                                                                                        // Approximate processing time
                                                                                        // manually if not tracked
                                                                                        // properly
                                                                                        Integer process_time = 1500; // Mocked
                                                                                                                     // for
                                                                                                                     // now

                                                                                        return document_repository
                                                                                                        .save(doc)
                                                                                                        .then(logging_service
                                                                                                                        .logVerification(
                                                                                                                                        final_type,
                                                                                                                                        status,
                                                                                                                                        reason,
                                                                                                                                        analysis.getConfidenceScore(),
                                                                                                                                        analysis,
                                                                                                                                        process_time))
                                                                                                        .thenReturn(analysis);
                                                                                });
                                                        });
                                }));
        }
}