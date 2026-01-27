package Projects.Network.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import Projects.Network.model.DocumentEntity;
import Projects.Network.model.User;
import Projects.Network.repository.DocumentRepository;
import Projects.Network.repository.UserRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * DocumentService
 *
 * Service responsible for managing document-related operations.
 *
 * This service handles the complete lifecycle of user documents, including:
 * - Validation of uploaded files (size and type)
 * - Storage of files in Supabase storage
 * - Persistence of document metadata in the database
 * - Retrieval and deletion of documents
 *
 * The service follows a reactive and non-blocking programming model
 * using Project Reactor and Spring WebFlux.
 *
 * This class belongs to the Service layer and contains business logic.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Service
public class DocumentService {

    /**
     * Maximum allowed file size for uploads (10 MB).
     */
    private static final long MAX_FILE_SIZE = 10_000_000L;

    /**
     * Allowed MIME type for PDF documents.
     */
    private static final String PDF_TYPE = "application/pdf";

    /**
     * Allowed MIME types for image files.
     */
    private static final String[] IMAGE_TYPES = {
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff"
    };

    /**
     * Repository used to persist and retrieve document metadata.
     */
    private final DocumentRepository repository;

    /**
     * Repository used to retrieve user information.
     */
    private final UserRepository userRepository;

    /**
     * Supabase storage service used for object storage operations.
     */
    private final SupabaseStorageService supabaseStorageService;

    /**
     * Constructs a new DocumentService with required dependencies.
     *
     * @param repository             document repository
     * @param userRepository         user repository
     * @param supabaseStorageService Supabase storage service
     */
    public DocumentService(DocumentRepository repository,
            UserRepository userRepository,
            SupabaseStorageService supabaseStorageService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.supabaseStorageService = supabaseStorageService;
    }

    /**
     * Uploads a document for a given user.
     *
     * This method performs the following steps:
     * - Verifies that the user exists
     * - Validates the uploaded file (type and size)
     * - Stores the file in Supabase
     * - Saves the document metadata in the database
     *
     * @param file      uploaded file
     * @param userId    identifier of the user
     * @param pieceType type of the document
     * @return a Mono emitting the saved DocumentEntity
     */
    public Mono<DocumentEntity> uploadDocument(FilePart file, UUID userId, String pieceType) {
        System.out.println("=== UPLOAD START ===");
        System.out.println("UserId: " + userId);
        System.out.println("PieceType: " + pieceType);
        System.out.println("Filename: " + file.filename());

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found with ID: " + userId)))
                .doOnNext(user -> System.out.println("User found: " + user.getEmail()))
                .flatMap(user -> validate(file)
                        .flatMap(validFile -> storeInSupabase(validFile, user, pieceType))
                        .flatMap(entity -> {
                            entity.setUserId(userId);
                            return repository.save(entity);
                        }))
                .doOnSuccess(doc -> System.out.println("Document saved: " + doc.getFileName()))
                .doOnError(e -> {
                    System.err.println("Upload error: " + e.getMessage());
                    e.printStackTrace();
                });
    }

    /**
     * Retrieves a document using its unique identifier.
     *
     * @param documentId identifier of the document
     * @return a Mono emitting the DocumentEntity if found
     */
    public Mono<DocumentEntity> getDocumentById(UUID documentId) {
        System.out.println("Fetching document with ID: " + documentId);
        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .doOnSuccess(doc -> System.out.println("Document found: " + doc.getFileName()))
                .doOnError(e -> System.err.println("Error fetching document: " + e.getMessage()));
    }

    /**
     * Retrieves all documents belonging to a specific user.
     *
     * @param userId identifier of the user
     * @return a Flux emitting all DocumentEntity objects for the user
     */
    public Flux<DocumentEntity> getUserDocuments(UUID userId) {
        System.out.println("Fetching documents for user: " + userId);
        return repository.findAllByUserId(userId)
                .doOnNext(doc -> System.out.println("Found document: " + doc.getFileName()))
                .doOnComplete(() -> System.out.println("Finished fetching user documents"))
                .doOnError(e -> System.err.println("Error fetching user documents: " + e.getMessage()));
    }

    /**
     * Deletes a document from both the database and Supabase storage.
     *
     * @param documentId identifier of the document
     * @return a Mono signaling completion of the delete operation
     */
    public Mono<Void> deleteDocument(UUID documentId) {
        System.out.println("Deleting document with ID: " + documentId);

        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .flatMap(doc -> {
                    System.out.println("Document found, deleting from Supabase: " + doc.getMinioPath());

                    return supabaseStorageService.deleteFile(doc.getMinioPath())
                            .doOnSuccess(
                                    v -> System.out.println("Document deleted from Supabase: " + doc.getMinioPath()))
                            .thenReturn(doc);
                })
                .flatMap(doc -> {
                    System.out.println("Deleting document from database...");
                    return repository.delete(doc)
                            .doOnSuccess(v -> System.out.println("Document deleted from database"));
                })
                .doOnError(e -> System.err.println("Error deleting document: " + e.getMessage()));
    }

    /**
     * Validates the uploaded file by checking its size and MIME type.
     *
     * @param file uploaded file
     * @return a Mono emitting the validated FilePart
     */
    private Mono<FilePart> validate(FilePart file) {
        String type = file.headers().getContentType() != null
                ? file.headers().getContentType().toString()
                : "";
        System.out.println("File type: " + type);

        return file.content()
                .reduce(0L, (size, buffer) -> size + buffer.readableByteCount())
                .flatMap(size -> {
                    System.out.println("File size: " + size + " bytes");
                    if (size > MAX_FILE_SIZE) {
                        return Mono.error(new IllegalArgumentException("File size exceeds 10MB limit."));
                    }
                    if (!PDF_TYPE.equals(type) && !isImageType(type)) {
                        return Mono
                                .error(new IllegalArgumentException("Only PDF and images are allowed. Type: " + type));
                    }
                    return Mono.just(file);
                });
    }

    /**
     * Checks whether the given MIME type corresponds to an allowed image type.
     *
     * @param type MIME type to check
     * @return true if the type is allowed, false otherwise
     */
    private boolean isImageType(String type) {
        for (String allowed : IMAGE_TYPES) {
            if (allowed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stores the uploaded file in Supabase and builds the corresponding
     * DocumentEntity.
     *
     * @param file      uploaded file
     * @param user      owner of the document
     * @param pieceType type of the document
     * @return a Mono emitting the created DocumentEntity
     */
    private Mono<DocumentEntity> storeInSupabase(FilePart file, User user, String pieceType) {
        String originalName = file.filename();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String customObjectName = pieceType + "_de_" + user.getLastName() + "_" + user.getFirstName() + "_"
                + UUID.randomUUID().toString() + extension;

        System.out.println("Supabase object name: " + customObjectName);

        return supabaseStorageService.uploadFile(file, customObjectName)
                .map(path -> {
                    DocumentEntity entity = new DocumentEntity();
                    entity.setFileName(customObjectName);
                    entity.setMinioPath(path);
                    entity.setPieceType(pieceType);
                    entity.setUserId(user.getUserId());
                    entity.setFileType(file.headers().getContentType() != null
                            ? file.headers().getContentType().toString()
                            : "application/octet-stream");
                    entity.setStatus("UPLOADED");
                    return entity;
                });
    }
}
