package Projects.Network.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import Projects.Network.model.DocumentEntity;
import Projects.Network.model.User;
import Projects.Network.repository.DocumentRepository;
import Projects.Network.repository.UserRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.io.ByteArrayInputStream;

/**
 * Service class that handles the core business logic for document management.
 * This includes user verification, file validation, storage in MinIO,
 * and persistence of metadata in PostgreSQL.
 */
@Service
public class DocumentService {

    // Constraints for file uploads
    private static final long MAX_FILE_SIZE = 1_000_000L; // 1 Megabyte
    private static final String PDF_TYPE = "application/pdf";
    private static final String[] IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif"};

    private final DocumentRepository repository;
    private final UserRepository userRepository;
    private final MinioClient minioClient;
    private final String bucketName;

    /**
     * Constructor-based Dependency Injection.
     * Bucket name is injected from application.properties.
     */
    public DocumentService(DocumentRepository repository,
                           UserRepository userRepository,
                           MinioClient minioClient,
                           @Value("${minio.bucket-name}") String bucketName) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    /**
     * Main workflow for uploading a document.
     * 1. Find user -> 2. Validate file -> 3. Upload to MinIO -> 4. Save to DB.
     * * @param file The binary file part from the request
     * @param userId ID of the user owning the document
     * @param pieceType Type of piece (passport/cni/permis)
     * @return A Mono emitting the saved DocumentEntity
     */
    public Mono<DocumentEntity> uploadDocument(FilePart file, UUID userId, String pieceType) {

        // Step 1: Verify that the user exists in the database
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user -> validate(file) // Step 2: Perform file checks
                        .flatMap(validFile -> storeInMinio(validFile, user, pieceType)) // Step 3: Send to MinIO
                        .flatMap(entity -> {
                            entity.setUserId(userId);
                            return repository.save(entity); // Step 4: Record metadata in DB
                        })
                );
    }

    /**
     * Validates file size and MIME type.
     * Uses reactive 'reduce' to calculate total size from data buffers.
     */
    private Mono<FilePart> validate(FilePart file) {
        String type = file.headers().getContentType().toString();

        return file.content()
                .reduce(0L, (size, buffer) -> size + buffer.readableByteCount())
                .flatMap(size -> {
                    // Check if file is too large
                    if (size > MAX_FILE_SIZE) {
                        return Mono.error(new IllegalArgumentException("File size exceeds 1MB limit."));
                    }
                    // Check if file format is allowed (PDF or specific Images)
                    if (!PDF_TYPE.equals(type) && !isImageType(type)) {
                        return Mono.error(new IllegalArgumentException("Only PDF and images are allowed."));
                    }
                    return Mono.just(file);
                });
    }

    /**
     * Helper method to check if the Content-Type matches allowed image formats.
     */
    private boolean isImageType(String type) {
        for (String allowed : IMAGE_TYPES) {
            if (allowed.equals(type)) return true;
        }
        return false;
    }

    /**
     * Handles the binary transfer to the MinIO server.
     * Generates a custom filename using user metadata.
     */
    private Mono<DocumentEntity> storeInMinio(FilePart file, User user, String pieceType) {
        String originalName = file.filename();
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";

        // Naming convention: PIECETYPE_de_LASTNAME_FIRSTNAME.ext
        String customObjectName = pieceType + "_de_" + user.getLastName() + "_" + user.getFirstName() + extension;

        // Collect all data buffers into a single byte array for MinIO upload
        return file.content()
                .map(buf -> buf.asByteBuffer())
                .reduce(new byte[0], (acc, bb) -> {
                    byte[] combined = new byte[acc.length + bb.remaining()];
                    System.arraycopy(acc, 0, combined, 0, acc.length);
                    bb.get(combined, acc.length, bb.remaining());
                    return combined;
                })
                .flatMap(bytes -> {
                    try {
                        // Execution of the upload to the MinIO bucket
                        minioClient.putObject(PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(customObjectName)
                                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                                .contentType(file.headers().getContentType().toString())
                                .build());

                        // Prepare the entity object for database storage
                        DocumentEntity entity = new DocumentEntity();
                        entity.setDocumentName(customObjectName);
                        entity.setMinioPath(customObjectName);
                        entity.setDocumentSize((long) bytes.length);
                        entity.setDocumentType(pieceType);
                        entity.setUserId(user.getUserId());

                        return Mono.just(entity);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to upload to MinIO: " + e.getMessage()));
                    }
                });
    }
}