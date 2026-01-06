package Projects.Network.controller;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.service.DocumentAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * DocumentAnalysisController
 *
 * REST controller for document analysis operations.
 * Exposes endpoints to analyze parsed documents and retrieve
 * structured information with validation results.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-06
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentAnalysisController {

    private final DocumentAnalysisService documentAnalysisService;

    /**
     * Analyzes a document and returns extracted information.
     *
     * @param documentId unique identifier of the document to analyze
     * @return a Mono emitting the analysis response
     */
    @PostMapping("/{documentId}/analyze")
    public Mono<ResponseEntity<DocumentAnalysisResponse>> analyzeDocument(
            @PathVariable UUID documentId) {
        
        System.out.println("Received analysis request for document: " + documentId);
        
        return documentAnalysisService.analyzeDocument(documentId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    System.err.println("Error analyzing document: " + e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }
}
