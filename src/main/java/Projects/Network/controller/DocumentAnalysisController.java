package Projects.Network.controller;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.model.DocumentEntity;
import Projects.Network.config.ReactiveTenantContext;
import Projects.Network.repository.DocumentRepository;
import Projects.Network.service.DocumentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.buffer.DataBufferUtils;
import Projects.Network.service.EnhancedDocumentService;
import Projects.Network.service.VerificationLoggingService;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DocumentAnalysisController {

    private final DocumentAnalysisService analysisService;
    private final DocumentRepository documentRepository;
    private final VerificationLoggingService loggingService;
    private final EnhancedDocumentService enhancedDocumentService;

    /**
     * Uploads and analyzes a document.
     * 
     * @param apiKey The platform's API Key (for Swagger visibility)
     * @param frontFileMono Front side of the document
     * @param backFileMono Back side of the document (optional)
     * @param pieceType Initial document type hint (optional)
     * @return Analysis results
     */
    @PostMapping(value = "/upload-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentAnalysisResponse> uploadAndAnalyze(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestPart("frontFile") Mono<FilePart> frontFileMono,
            @RequestPart(value = "backFile", required = false) Mono<FilePart> backFileMono) {

        return ReactiveTenantContext.getPlatform()
                .switchIfEmpty(Mono.error(new RuntimeException("Platform not found/Invalid API Key")))
                .flatMap(platform -> frontFileMono.flatMap(frontFile -> {
                    log.info("Starting analysis for platform {} with file {}", platform.getId(),
                            frontFile.filename());

                    Mono<byte[]> frontBytesMono = DataBufferUtils.join(frontFile.content())
                            .map(db -> {
                                byte[] b = new byte[db.readableByteCount()];
                                db.read(b);
                                DataBufferUtils.release(db);
                                return b;
                            });

                    Mono<byte[]> backBytesMono = backFileMono
                            .ofType(FilePart.class)
                            .flatMap(bf -> DataBufferUtils.join(bf.content())
                                    .map(db -> {
                                        byte[] b = new byte[db.readableByteCount()];
                                        db.read(b);
                                        DataBufferUtils.release(db);
                                        return b;
                                    }))
                            .defaultIfEmpty(new byte[0]);

                    return Mono.zip(frontBytesMono, backBytesMono)
                            .flatMap(bytesTuple -> {
                                byte[] frontBytes = bytesTuple.getT1();
                                byte[] backBytes = bytesTuple.getT2();

                                Mono<String> frontOcr = enhancedDocumentService
                                        .extractMarkdownFromBytes(frontBytes,
                                                frontFile.filename().toLowerCase().endsWith(".pdf"));
                                Mono<String> backOcr = backBytes.length > 0
                                        ? enhancedDocumentService.extractMarkdownFromBytes(backBytes,
                                                frontFile.filename().toLowerCase().endsWith(".pdf"))
                                        : Mono.just("");

                                return Mono.zip(frontOcr, backOcr)
                                        .flatMap(ocrTuple -> analysisService.analyzeFull(ocrTuple.getT1(),
                                                ocrTuple.getT2()))
                                        .flatMap(analysis -> {
                                            String finalType = analysis.getDocumentType();

                                            DocumentEntity doc = DocumentEntity.builder()
                                                    .platformId(platform.getId())
                                                    .pieceType(finalType)
                                                    .status("VERIFIED")
                                                    .uploadDate(java.time.LocalDateTime.now())
                                                    .build();

                                            log.info("Saving document record for platform {}", platform.getId());
                                            
                                            String status = analysis.getIsValid() ? "ACCEPTED" : "REJECTED";
                                            String reason = analysis.getIsValid() ? null : analysis.getValidationMessage();

                                            return documentRepository.save(doc)
                                                    .then(loggingService.logVerification(finalType, status, reason, analysis.getConfidenceScore()))
                                                    .thenReturn(analysis);
                                        });
                            });
                }));
    }
}