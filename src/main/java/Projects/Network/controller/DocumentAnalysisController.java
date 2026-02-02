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
                        @RequestPart("frontFile") Mono<FilePart> frontFileMono,
                        @RequestPart(value = "backFile", required = false) Mono<FilePart> backFileMono,
                        @RequestPart(value = "pieceType", required = false) String pieceType,
                        @RequestPart("userId") String userId) {

                UUID userUuid;
                try {
                        userUuid = UUID.fromString(userId);
                } catch (IllegalArgumentException e) {
                        return Mono.error(new Projects.Network.exception.ResourceNotFoundException(
                                        "Invalid user ID format: " + userId));
                }

                return userRepository.findById(userUuid)
                                .switchIfEmpty(Mono.error(new Projects.Network.exception.ResourceNotFoundException(
                                                "User not found with ID: " + userId)))
                                .flatMap(user -> {
                                        String cleanPieceType = (pieceType != null && !pieceType.isEmpty()) ? pieceType
                                                        : "UNKNOWN";
                                        String baseName = cleanPieceType + "_de_" +
                                                        (user.getLastName() != null
                                                                        ? user.getLastName().replaceAll("\\s+", "")
                                                                        : "NOLASTNAME")
                                                        + "_" +
                                                        (user.getFirstName() != null
                                                                        ? user.getFirstName().replaceAll("\\s+", "")
                                                                        : "NOFIRSTNAME");

                                        // Ensure backFileMono is handled even if it's not provided in the request
                                        Mono<FilePart> safeBackFileMono = backFileMono != null ? backFileMono
                                                        : Mono.empty();

                                        return frontFileMono
                                                        .switchIfEmpty(Mono.error(
                                                                        new RuntimeException("Front file is missing")))
                                                        .flatMap(frontFile -> {
                                                                String frontExt = getExtension(frontFile.filename());
                                                                String frontPath = "documents/" + baseName + "_front"
                                                                                + frontExt;

                                                                return supabaseStorageService
                                                                                .uploadFile(frontFile, frontPath)
                                                                                .flatMap(uploadedFrontPath -> {
                                                                                        return safeBackFileMono.flatMap(
                                                                                                        backFile -> {
                                                                                                                String backExt = getExtension(
                                                                                                                                backFile.filename());
                                                                                                                String backPath = "documents/"
                                                                                                                                + baseName
                                                                                                                                + "_back"
                                                                                                                                + backExt;
                                                                                                                return supabaseStorageService
                                                                                                                                .uploadFile(backFile,
                                                                                                                                                backPath)
                                                                                                                                .flatMap(uploadedBackPath -> saveAndAnalyze(
                                                                                                                                                uploadedFrontPath,
                                                                                                                                                uploadedBackPath,
                                                                                                                                                cleanPieceType,
                                                                                                                                                user,
                                                                                                                                                baseName + frontExt,
                                                                                                                                                frontFile));
                                                                                                        })
                                                                                                        .switchIfEmpty(
                                                                                                                        saveAndAnalyze(uploadedFrontPath,
                                                                                                                                        null,
                                                                                                                                        cleanPieceType,
                                                                                                                                        user,
                                                                                                                                        baseName + frontExt,
                                                                                                                                        frontFile));
                                                                                });
                                                        });
                                });
        }

        @GetMapping("/{documentId}/analyze")
        public Mono<DocumentAnalysisResponse> analyzeExisting(@PathVariable UUID documentId) {
                return analysisService.analyzeDocument(documentId);
        }

        private Mono<DocumentAnalysisResponse> saveAndAnalyze(String frontPath, String backPath, String pieceType,
                        User user, String fileName, FilePart frontPart) {

                String contentType = frontPart.headers().getContentType() != null
                                ? frontPart.headers().getContentType().toString()
                                : "application/octet-stream";

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
                return (filename != null && filename.contains(".")) ? filename.substring(filename.lastIndexOf("."))
                                : "";
        }
}