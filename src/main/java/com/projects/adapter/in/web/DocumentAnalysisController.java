package com.projects.adapter.in.web;

import com.projects.adapter.in.web.dto.DocumentAnalysisResponse;
import com.projects.config.ReactiveTenantContext;
import com.projects.application.port.in.document.AnalyzeDocumentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DocumentAnalysisController {

    private final AnalyzeDocumentUseCase analyzeDocumentUseCase;

    @PostMapping(value = "/upload-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentAnalysisResponse> uploadAndAnalyze(
            @RequestHeader("X-API-KEY") String apiKey,
            @RequestPart("frontFile") Mono<FilePart> frontFileMono,
            @RequestPart(value = "backFile", required = false) Mono<FilePart> backFileMono) {

        return ReactiveTenantContext.getPlatform()
            .switchIfEmpty(Mono.error(new RuntimeException("Platform not found/Invalid API Key")))
            .flatMap(platform -> frontFileMono.flatMap(frontFile -> {
                log.info("Starting analysis for platform {} with file {}", platform.getId(), frontFile.filename());

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
                        
                        return analyzeDocumentUseCase.analyzeDocument(
                                frontBytes, 
                                backBytes.length > 0 ? backBytes : null, 
                                frontFile.filename(), 
                                platform.getId());
                    });
            }));
    }
}