package Projects.Network.controller;

import Projects.Network.dto.DocumentAnalysisResponse;
import Projects.Network.model.DocumentEntity;
import Projects.Network.model.User;
import Projects.Network.repository.DocumentRepository;
import Projects.Network.repository.UserRepository;
import Projects.Network.service.DocumentAnalysisService;
import Projects.Network.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.buffer.DataBufferUtils;
import Projects.Network.service.EnhancedDocumentService;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DocumentAnalysisController {

        private final DocumentAnalysisService analysisService;
        private final SupabaseStorageService supabaseStorageService;
        private final DocumentRepository documentRepository;
        private final UserRepository userRepository;
        private final EnhancedDocumentService enhancedDocumentService;

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
                        return Mono.error(new RuntimeException("Invalid user ID format: " + userId));
                }

                return userRepository.findById(userUuid)
                                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                                .flatMap(user -> frontFileMono.flatMap(frontFile -> {
                                        log.info("Starting analysis for user {} with file {}", user.getUserId(),
                                                        frontFile.filename());

                                        Mono<byte[]> frontBytesMono = DataBufferUtils.join(frontFile.content())
                                                        .map(db -> {
                                                                byte[] b = new byte[db.readableByteCount()];
                                                                db.read(b);
                                                                DataBufferUtils.release(db);
                                                                log.debug("Read {} bytes for front file", b.length);
                                                                return b;
                                                        });

                                        Mono<byte[]> backBytesMono = backFileMono
                                                        .flatMap(bf -> DataBufferUtils.join(bf.content())
                                                                        .map(db -> {
                                                                                byte[] b = new byte[db
                                                                                                .readableByteCount()];
                                                                                db.read(b);
                                                                                DataBufferUtils.release(db);
                                                                                log.debug("Read {} bytes for back file",
                                                                                                b.length);
                                                                                return b;
                                                                        }))
                                                        .defaultIfEmpty(new byte[0]);

                                        return Mono.zip(frontBytesMono, backBytesMono)
                                                        .flatMap(bytesTuple -> {
                                                                byte[] frontBytes = bytesTuple.getT1();
                                                                byte[] backBytes = bytesTuple.getT2();

                                                                Mono<String> frontOcr = enhancedDocumentService
                                                                                .extractMarkdownFromBytes(frontBytes,
                                                                                                frontFile.filename()
                                                                                                                .toLowerCase()
                                                                                                                .endsWith(".pdf"));
                                                                Mono<String> backOcr = backBytes.length > 0
                                                                                ? enhancedDocumentService
                                                                                                .extractMarkdownFromBytes(
                                                                                                                backBytes,
                                                                                                                frontFile.filename()
                                                                                                                                .toLowerCase()
                                                                                                                                .endsWith(".pdf")) // assuming
                                                                                                                                                   // same
                                                                                                                                                   // type
                                                                                : Mono.just("");

                                                                log.info("OCR completed for user {}. Starting analysis...",
                                                                                user.getUserId());
                                                                return Mono.zip(frontOcr, backOcr)
                                                                                .flatMap(ocrTuple -> {
                                                                                        log.info("Analysis engine triggered for user {}",
                                                                                                        user.getUserId());
                                                                                        return analysisService
                                                                                                        .analyzeFull(ocrTuple
                                                                                                                        .getT1(),
                                                                                                                        ocrTuple.getT2())
                                                                                                        .flatMap(analysis -> {
                                                                                                                String finalType = analysis
                                                                                                                                .getDocumentType();
                                                                                                                log.info("Analysis result: type={}",
                                                                                                                                finalType);
                                                                                                                if (finalType == null
                                                                                                                                || finalType.equals(
                                                                                                                                                "UNKNOWN")) {
                                                                                                                        finalType = (pieceType != null
                                                                                                                                        && !pieceType.isEmpty())
                                                                                                                                                        ? pieceType
                                                                                                                                                        : "UNKNOWN";
                                                                                                                }

                                                                                                                String baseName = finalType
                                                                                                                                + "_de_"
                                                                                                                                + user.getLastName()
                                                                                                                                + "_"
                                                                                                                                + user.getFirstName();
                                                                                                                String ext = getExtension(
                                                                                                                                frontFile.filename());
                                                                                                                String frontPath = "documents/"
                                                                                                                                + baseName
                                                                                                                                + "_front"
                                                                                                                                + ext;
                                                                                                                String backPath = backBytes.length > 0
                                                                                                                                ? "documents/" + baseName
                                                                                                                                                + "_back"
                                                                                                                                                + ext
                                                                                                                                : null;

                                                                                                                String contentType = frontFile
                                                                                                                                .headers()
                                                                                                                                .getContentType() != null
                                                                                                                                                ? frontFile.headers()
                                                                                                                                                                .getContentType()
                                                                                                                                                                .toString()
                                                                                                                                                : "application/octet-stream";

                                                                                                                DocumentEntity doc = DocumentEntity
                                                                                                                                .builder()
                                                                                                                                .userId(user.getUserId())
                                                                                                                                .pieceType(finalType)
                                                                                                                                .fileName(baseName
                                                                                                                                                + ext)
                                                                                                                                .minioPath(frontPath)
                                                                                                                                .backMinioPath(backPath)
                                                                                                                                .fileType(contentType)
                                                                                                                                .fileSize((long) frontBytes.length)
                                                                                                                                .status("UPLOADED")
                                                                                                                                .build();

                                                                                                                log.info("Saving document record to database for user {}",
                                                                                                                                user.getUserId());
                                                                                                                return documentRepository
                                                                                                                                .save(doc)
                                                                                                                                .flatMap(saved -> {
                                                                                                                                        log.info("Uploading files to Supabase: {}",
                                                                                                                                                        frontPath);
                                                                                                                                        Mono<String> upFront = supabaseStorageService
                                                                                                                                                        .uploadFile(frontPath,
                                                                                                                                                                        frontBytes,
                                                                                                                                                                        contentType);
                                                                                                                                        Mono<String> upBack = backPath != null
                                                                                                                                                        ? supabaseStorageService
                                                                                                                                                                        .uploadFile(backPath,
                                                                                                                                                                                        backBytes,
                                                                                                                                                                                        contentType)
                                                                                                                                                        : Mono.just("");

                                                                                                                                        return Mono.zip(upFront,
                                                                                                                                                        upBack)
                                                                                                                                                        .doOnSuccess(v -> log
                                                                                                                                                                        .info("Upload completed for user {}",
                                                                                                                                                                                        user.getUserId()))
                                                                                                                                                        .thenReturn(analysis);
                                                                                                                                });
                                                                                                        });
                                                                                });
                                                        });
                                }));
        }


        private String getExtension(String filename) {
                return (filename != null && filename.contains(".")) ? filename.substring(filename.lastIndexOf("."))
                                : "";
        }
}