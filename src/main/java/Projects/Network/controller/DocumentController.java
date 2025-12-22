package Projects.Network.controller;

import Projects.Network.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import Projects.Network.service.DocumentService;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

/**
 * Controller for handling document operations with explicit feedback.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, String>>> upload(
            @RequestPart("file") FilePart file,
            @RequestPart("pieceType") String pieceType,
            @AuthenticationPrincipal User principal) {

        return documentService.uploadDocument(file, principal.getUserId(), pieceType)
                .map(doc -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Upload successful!");
                    response.put("documentId", doc.getDocumentId().toString());
                    response.put("fileName", file.filename());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                // Si le service ne retourne rien, on envoie quand même un message
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Upload failed: Document service returned no data")));
    }
}