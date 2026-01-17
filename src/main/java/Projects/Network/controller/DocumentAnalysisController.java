package Projects.Network.controller;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.model.DocumentEntity;
import Projects.Network.model.User;
import Projects.Network.repository.DocumentRepository;
import Projects.Network.repository.UserRepository;
import Projects.Network.service.DocumentAnalysisService;
import Projects.Network.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentAnalysisController {

    private final DocumentAnalysisService analysisService;
    private final SupabaseStorageService supabaseStorageService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Upload TWO separate files (front + back) and analyze
     */
    @PostMapping(value = "/upload-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentAnalysisResponse> uploadAndAnalyze(
            @RequestPart("frontFile") Mono<FilePart> frontFile,
            @RequestPart(value = "backFile", required = false) Mono<FilePart> backFile,
            @RequestPart(value="pieceType", required = false) String pieceType,
            @RequestPart("userId") String userId) {

        UUID userUuid = UUID.fromString(userId);

        return userRepository.findById(userUuid)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found: " + userId)))
                .flatMap(user -> {
                    String cleanPieceType = (pieceType != null && !pieceType.isEmpty()) ? pieceType : "UNKNOWN";
                    // Construct base filename: TYPE_LASTNAME_FIRSTNAME
                    String baseName = cleanPieceType + "_de_" + 
                                      (user.getLastName() != null ? user.getLastName().replaceAll("\\s+", "") : "NOLASTNAME") + "_" + 
                                      (user.getFirstName() != null ? user.getFirstName().replaceAll("\\s+", "") : "NOFIRSTNAME");

                    return frontFile.flatMap(front -> {
                        String frontExt = getExtension(front.filename());
                        String frontPath = "documents/" + baseName + "_front" + frontExt;
                        
                        return supabaseStorageService.uploadFile(front, frontPath)
                                .flatMap(uploadedFrontPath -> {
                                    if (backFile != null) {
                                        return backFile.flatMap(back -> {
                                            String backExt = getExtension(back.filename());
                                            String backPath = "documents/" + baseName + "_back" + backExt;
                                            return supabaseStorageService.uploadFile(back, backPath)
                                                    .flatMap(uploadedBackPath -> 
                                                        saveAndAnalyze(uploadedFrontPath, uploadedBackPath, cleanPieceType, user, baseName + frontExt, front)
                                                    );
                                        });
                                    } else {
                                        return saveAndAnalyze(uploadedFrontPath, null, cleanPieceType, user, baseName + frontExt, front);
                                    }
                                });
                    });
                });
    }

    @GetMapping("/{documentId}/analyze")
    public Mono<DocumentAnalysisResponse> analyzeExisting(@PathVariable UUID documentId) {
        return analysisService.analyzeDocument(documentId);
    }

    private Mono<DocumentAnalysisResponse> saveAndAnalyze(String frontPath, String backPath, String pieceType, User user, String fileName, FilePart frontPart) {
        
        String contentType = frontPart.headers().getContentType() != null ? 
                             frontPart.headers().getContentType().toString() : "application/octet-stream";
        
        DocumentEntity doc = DocumentEntity.builder()
                .minioPath(frontPath)
                .backMinioPath(backPath)
                .pieceType(pieceType)
                .userId(user.getUserId())
                .fileName(fileName)
                .fileType(contentType)
                .fileSize(0L)
                .status("UPLOADED")
                .build();

        return documentRepository.save(doc)
                .flatMap(saved -> analysisService.analyzeDocument(saved.getId()));
    }

    private String getExtension(String filename) {
        return (filename != null && filename.contains(".")) ? 
                filename.substring(filename.lastIndexOf(".")) : "";
    }
}