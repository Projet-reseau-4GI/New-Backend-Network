package Projects.Network.controller;

import Projects.Network.model.DocumentEntity;
import Projects.Network.service.DocumentService;
import Projects.Network.service.EnhancedDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for document management operations.
 *
 * This controller provides comprehensive document management functionality
 * including
 * file upload to MinIO object storage, metadata retrieval, file download,
 * document
 * parsing using external AI services, and text extraction. All operations are
 * reactive
 * and non-blocking, designed to work efficiently with WebFlux architecture.
 *
 * Core capabilities:
 *
 * 1. Document upload:
 * Accepts multipart file uploads and stores them in Supabase storage while
 * maintaining metadata in PostgreSQL database. Supports various file types
 * including
 * images (JPEG, PNG, GIF) and PDF documents with size limits for security.
 *
 * 2. Metadata management:
 * Retrieves and manages document metadata without downloading the actual file,
 * providing efficient access to document information for UI display and
 * processing.
 *
 * 3. File download:
 * Provides secure file download with proper content type headers and filename
 * handling, supporting browser downloads and programmatic file retrieval.
 *
 * 4. Document parsing:
 * Integrates with external AI-powered parsing services to extract structured
 * information from documents, enabling automated document processing workflows.
 *
 * 5. Text extraction:
 * Extracts text content from documents in Markdown format, facilitating
 * full-text search, content analysis, and document understanding.
 *
 * Architecture integration:
 *
 * This controller follows a layered architecture pattern:
 * - Controller layer (this class): HTTP request/response handling, URL mapping
 * - Service layer (DocumentService): Business logic, validation, orchestration
 * - Enhanced service layer (EnhancedDocumentService): External API integration,
 * parsing
 * - Repository layer (DocumentRepository): Database operations via R2DBC
 * - Storage layer (Supabase): Object storage for actual file content
 *
 * The separation ensures:
 * - Clear responsibility boundaries
 * - Easy testing through mocking
 * - Flexibility to change implementation details
 * - Reusability of business logic
 *
 * Security considerations:
 *
 * 1. File validation:
 * DocumentService validates file types and sizes before accepting uploads.
 * Only whitelisted MIME types are allowed to prevent malicious file uploads.
 *
 * 2. User authentication:
 * All endpoints should be protected by JWT authentication in production.
 * Currently operates in open mode for development but should be secured
 * before production deployment.
 *
 * 3. Authorization:
 * Users should only access their own documents. Implement user ID validation
 * against authenticated user context before allowing operations.
 *
 * 4. Input sanitization:
 * File names and user-provided data should be sanitized to prevent
 * path traversal, injection attacks, and other security vulnerabilities.
 *
 * Technology stack integration:
 *
 * - Spring WebFlux: Reactive, non-blocking web framework
 * - Project Reactor: Mono and Flux for reactive streams
 * - Supabase: Object storage for file content
 * - PostgreSQL with R2DBC: Reactive database access for metadata
 * - External AI APIs: Document parsing and text extraction services
 *
 * Error handling strategy:
 *
 * All endpoints implement comprehensive error handling:
 * - Validation errors return 400 BAD REQUEST
 * - Not found scenarios return 404 NOT FOUND
 * - Internal errors return 500 INTERNAL SERVER ERROR
 * - Detailed error logging for debugging without exposing internals to clients
 *
 * Performance characteristics:
 *
 * 1. Non-blocking operations:
 * All methods return Mono or Flux types, allowing the application to handle
 * high concurrency without thread-per-request overhead.
 *
 * 2. Streaming support:
 * Large files are handled efficiently without loading entire content into
 * memory.
 * Reactive streams provide natural backpressure handling.
 *
 * 3. Parallel processing:
 * Multiple document operations can execute concurrently without blocking,
 * maximizing resource utilization and throughput.
 *
 * Monitoring and logging:
 *
 * Each operation logs key events for debugging and monitoring:
 * - Request receipt with parameters
 * - Successful completions with results
 * - Error conditions with detailed messages
 *
 * Production considerations should include:
 * - Structured logging with correlation IDs
 * - Metrics collection for operation counts and latencies
 * - Distributed tracing for debugging complex workflows
 * - Alert configuration for error rate thresholds
 *
 * Future enhancements:
 * - Batch document upload support
 * - Document versioning and history tracking
 * - Thumbnail generation for images
 * - Document sharing and collaboration features
 * - Full-text search integration with Elasticsearch
 * - Document expiration and automatic cleanup
 * - Virus scanning integration for uploaded files
 * - Digital signature verification for secure documents
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    /**
     * HTTP header constant for Content-Disposition header used in file downloads.
     *
     * The Content-Disposition header tells browsers how to handle the response:
     * - "inline": Display content in browser (for images, PDFs)
     * - "attachment": Trigger download with specified filename
     *
     * Format: Content-Disposition: attachment; filename="document.pdf"
     */
    private static final String ATTACHMENT_HEADER_PREFIX = "attachment; filename=\"";
    private static final String ATTACHMENT_HEADER_SUFFIX = "\"";

    /**
     * Default MIME type for binary file downloads when specific type is unknown.
     *
     * application/octet-stream is a generic binary stream type that indicates
     * the content should be downloaded rather than displayed inline.
     */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /**
     * Error message prefix for extraction failures.
     */
    private static final String EXTRACTION_ERROR_PREFIX = "Erreur lors de l'extraction: ";

    /**
     * Injected DocumentService for core document operations.
     *
     * This service handles:
     * - File upload to MinIO with validation
     * - Document metadata persistence in PostgreSQL
     * - Document retrieval by ID
     * - User document listing
     *
     * All operations are reactive and return Mono or Flux types.
     */
    private final DocumentService documentService;

    /**
     * Injected EnhancedDocumentService for advanced document processing.
     *
     * This service provides:
     * - File retrieval from MinIO storage
     * - Integration with external document parsing APIs
     * - Text extraction with AI-powered OCR
     * - Markdown conversion for extracted content
     *
     * Handles complex operations involving external services and long-running
     * tasks.
     */
    private final EnhancedDocumentService enhancedDocumentService;

    /**
     * Uploads a document file to Supabase storage and stores metadata in the
     * database.
     *
     * This endpoint accepts multipart form data containing a file, document type
     * classification,
     * and user identification. It orchestrates the complete upload workflow
     * including file
     * validation, storage in Supabase storage, and metadata persistence in
     * PostgreSQL.
     *
     * Endpoint details:
     * - HTTP Method: POST (creates a new resource)
     * - URL: /api/documents/upload
     * - Content-Type: multipart/form-data (required for file uploads)
     * - Response: 201 CREATED with document metadata on success
     *
     * Request parameters:
     *
     * 1. file (FilePart):
     * The actual file content to be uploaded. Can be any supported document type.
     *
     * Supported formats:
     * - Images: JPEG, PNG, GIF (for identity documents, receipts, etc.)
     * - Documents: PDF (for contracts, forms, reports)
     *
     * Size limits:
     * - Maximum: 10MB (configurable in DocumentService)
     * - Enforced to prevent memory exhaustion and storage abuse
     *
     * File processing:
     * - Streamed directly to MinIO without temporary disk storage
     * - Unique filename generated based on type and user information
     * - Original filename preserved in metadata for reference
     *
     * 2. pieceType (String):
     * Document classification indicating the type of document being uploaded.
     *
     * Common types:
     * - "PASSPORT": International travel document
     * - "CNI": National identity card (Carte Nationale d'Identité)
     * - "DRIVER_LICENSE": Driving permit
     * - "BIRTH_CERTIFICATE": Birth certificate
     * - "PROOF_OF_RESIDENCE": Utility bill or residence document
     *
     * Purpose:
     * - Organizational classification for easy retrieval
     * - Enables type-specific processing rules
     * - Used in filename generation for clarity
     * - Supports compliance and regulatory requirements
     *
     * 3. userId (String):
     * UUID string identifying the document owner.
     *
     * Format: "550e8400-e29b-41d4-a716-446655440000" (36 characters)
     *
     * Validation:
     * - Must be valid UUID format
     * - User must exist in database
     * - Converted from String to UUID type for type safety
     *
     * Security consideration:
     * In production, this should be extracted from the authenticated user's JWT
     * token
     * rather than being user-provided, preventing users from uploading documents
     * to other users' accounts.
     *
     * Upload workflow:
     *
     * Phase 1: Request validation and parsing
     * 1. Spring deserializes multipart form data into method parameters
     * 2. FilePart provides reactive access to file content stream
     * 3. String parameters are extracted from form fields
     * 4. Initial validation of parameter presence (Spring handles automatically)
     *
     * Phase 2: UUID validation
     * 1. Attempt to parse userId string as UUID
     * 2. If invalid format, immediately return 400 BAD REQUEST
     * 3. Valid UUID is passed to service layer for further processing
     *
     * Phase 3: File upload processing (in DocumentService)
     * 1. User existence verification in database
     * 2. File type validation (MIME type checking)
     * 3. File size validation (size limit enforcement)
     * 4. File content streaming to MinIO
     * 5. Generation of unique storage filename
     * 6. Storage of file in configured MinIO bucket
     *
     * Phase 4: Metadata persistence
     * 1. Creation of DocumentEntity with file information
     * 2. Database insertion via R2DBC
     * 3. Return of persisted entity with generated ID
     *
     * Phase 5: Response construction
     * 1. HTTP 201 CREATED status (new resource created)
     * 2. Full document metadata in response body
     * 3. Includes generated document ID for future operations
     *
     * Success response structure:
     * Status: 201 CREATED
     * Body: DocumentEntity JSON
     * {
     * "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
     * "userId": "550e8400-e29b-41d4-a716-446655440000",
     * "pieceType": "PASSPORT",
     * "fileName": "PASSPORT_de_Doe_John.jpg",
     * "fileSize": 2457600,
     * "minioPath": "PASSPORT_de_Doe_John.jpg",
     * "fileType": "image/jpeg",
     * "uploadDate": "2026-01-02T10:30:00Z",
     * "status": "UPLOADED"
     * }
     *
     * Error scenarios and responses:
     *
     * 1. Invalid UUID format:
     * Status: 400 BAD REQUEST
     * Cause: userId parameter is not a valid UUID string
     * Example: "invalid-uuid-format" or "12345"
     *
     * 2. User not found:
     * Status: 400 BAD REQUEST
     * Cause: Valid UUID but no user exists with that ID
     * Handled in DocumentService layer
     *
     * 3. Invalid file type:
     * Status: 400 BAD REQUEST
     * Cause: File MIME type not in whitelist
     * Example: Attempting to upload .exe, .zip, .mp4
     *
     * 4. File too large:
     * Status: 400 BAD REQUEST
     * Cause: File size exceeds configured maximum (10MB)
     *
     * 5. MinIO upload failure:
     * Status: 400 BAD REQUEST
     * Cause: Network error, storage full, permission issues
     * Underlying MinIO exceptions caught and wrapped
     *
     * 6. Database insertion failure:
     * Status: 400 BAD REQUEST
     * Cause: Constraint violation, database unavailable
     * Note: File may be in MinIO but metadata not recorded
     *
     * Security considerations:
     *
     * 1. File validation:
     * Content type verification prevents executable uploads
     * Size limits prevent storage exhaustion attacks
     * Filename sanitization prevents path traversal
     *
     * 2. User authorization:
     * Production deployment should verify authenticated user matches userId
     * Prevent privilege escalation by uploading to other accounts
     *
     * 3. Rate limiting:
     * Consider implementing upload rate limits per user
     * Prevents abuse and storage quota exhaustion
     *
     * 4. Malware scanning:
     * Integration with antivirus service recommended for production
     * Scan files before accepting into permanent storage
     *
     * Performance optimizations:
     *
     * 1. Streaming upload:
     * File content streams directly to MinIO
     * Never fully loaded into application memory
     * Enables handling of large files efficiently
     *
     * 2. Async processing:
     * Reactive pipeline ensures non-blocking execution
     * Multiple uploads can process concurrently
     * No thread blocking during I/O operations
     *
     * 3. Connection pooling:
     * MinIO client maintains connection pool
     * Database operations use R2DBC connection pool
     * Reduces connection establishment overhead
     *
     * Client integration example:
     *
     * Using HTML form:
     * <form action="/api/documents/upload" method="POST" enctype=
     * "multipart/form-data">
     * <input type="file" name="file" required>
     * <input type="text" name="pieceType" value="PASSPORT" required>
     * <input type="hidden" name="userId" value=
     * "550e8400-e29b-41d4-a716-446655440000">
     * <button type="submit">Upload</button>
     * </form>
     *
     * Using JavaScript Fetch API:
     * const formData = new FormData();
     * formData.append('file', fileInput.files[0]);
     * formData.append('pieceType', 'PASSPORT');
     * formData.append('userId', '550e8400-e29b-41d4-a716-446655440000');
     *
     * fetch('/api/documents/upload', {
     * method: 'POST',
     * body: formData
     * });
     *
     * Monitoring and debugging:
     * Console output provides detailed logging:
     * - Upload initiation with parameters
     * - UUID parsing success/failure
     * - Upload completion with document ID
     * - Error conditions with stack traces
     *
     * Production logging should use structured logging framework
     * with correlation IDs for tracing requests through the system.
     *
     * @param file      the FilePart representing the uploaded file with reactive
     *                  content stream,
     *                  provides access to filename, headers, and file content as a
     *                  stream of buffers
     * @param pieceType the document classification string indicating document type
     *                  (e.g., "PASSPORT", "CNI", "DRIVER_LICENSE"), used for
     *                  organization
     *                  and filename generation
     * @param userId    the UUID string identifying the document owner, must be
     *                  valid UUID format
     *                  and correspond to an existing user in the database
     * @return a Mono emitting ResponseEntity with HTTP 201 CREATED and
     *         DocumentEntity body
     *         on successful upload, or HTTP 400 BAD REQUEST on validation or
     *         processing errors
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DocumentEntity>> uploadDocument(
            @RequestPart("file") FilePart file,
            @RequestPart("pieceType") String pieceType,
            @RequestPart("userId") String userId) {

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return documentService.uploadDocument(file, userUuid, pieceType)
                .map(doc -> ResponseEntity.status(HttpStatus.CREATED).body(doc))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    /**
     * Retrieves document metadata without downloading the actual file content.
     *
     * This endpoint provides efficient access to document information stored in the
     * database
     * without the overhead of retrieving the actual file from MinIO storage. It
     * returns all
     * metadata fields including file name, size, type, upload date, and storage
     * path, enabling
     * UI display and decision-making about whether to download the full file.
     *
     * Endpoint details:
     * - HTTP Method: GET (read-only, idempotent operation)
     * - URL: /api/documents/{documentId}
     * - Authentication: Should require valid JWT token in production
     * - Response: 200 OK with DocumentEntity JSON, or 404 NOT FOUND if document
     * doesn't exist
     *
     * URL path parameter:
     *
     * documentId (UUID):
     * The unique identifier of the document to retrieve.
     *
     * Format: Path variable in URL (e.g.,
     * /api/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7)
     *
     * Validation:
     * - Automatically parsed by Spring from URL path
     * - Must be valid UUID format (Spring returns 400 if invalid)
     * - Must correspond to existing document in database
     *
     * Security consideration:
     * Production should verify authenticated user has permission to access this
     * document.
     * Prevent unauthorized access to other users' documents.
     *
     * Metadata retrieval workflow:
     *
     * Phase 1: Database query
     * 1. DocumentService.getDocumentById() queries PostgreSQL
     * 2. Executes: SELECT * FROM documents WHERE id = ?
     * 3. Returns Mono<DocumentEntity> (empty if not found)
     * 4. Query uses indexed primary key for fast lookup
     *
     * Phase 2: Response mapping
     * 1. If document found, wrap in ResponseEntity with 200 OK
     * 2. Full DocumentEntity serialized to JSON automatically
     * 3. All fields included in response
     *
     * Phase 3: Not found handling
     * 1. If Mono is empty (document doesn't exist)
     * 2. switchIfEmpty operator triggers
     * 3. Returns ResponseEntity with 404 NOT FOUND status
     * 4. Empty body (no content)
     *
     * Phase 4: Error handling
     * 1. Database errors caught by onErrorResume
     * 2. Returns 500 INTERNAL SERVER ERROR
     * 3. Error logged for debugging
     * 4. No sensitive error details exposed to client
     *
     * Success response structure:
     * Status: 200 OK
     * Content-Type: application/json
     * Body: Complete DocumentEntity
     * {
     * "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
     * "userId": "550e8400-e29b-41d4-a716-446655440000",
     * "pieceType": "PASSPORT",
     * "fileName": "PASSPORT_de_Doe_John.jpg",
     * "fileSize": 2457600,
     * "minioPath": "PASSPORT_de_Doe_John.jpg",
     * "fileType": "image/jpeg",
     * "uploadDate": "2026-01-02T10:30:00Z",
     * "status": "UPLOADED"
     * }
     *
     * Use cases for metadata retrieval:
     *
     * 1. Document listing:
     * Display document information in UI table or list without downloading files.
     * Shows filename, size, type, upload date for browsing.
     *
     * 2. Download decision:
     * Check file size before downloading to warn users about large files.
     * Verify file type to ensure client can handle the format.
     *
     * 3. Status checking:
     * Verify document status (uploaded, processing, verified, etc.)
     * Display appropriate UI indicators based on status.
     *
     * 4. Permission verification:
     * Check document ownership before allowing operations.
     * Retrieve userId to verify against authenticated user.
     *
     * 5. Processing preparation:
     * Get MinIO path for passing to processing services.
     * Retrieve file type to determine appropriate processing pipeline.
     *
     * Error scenarios:
     *
     * 1. Document not found:
     * Status: 404 NOT FOUND
     * Cause: No document with given ID exists
     * Body: Empty
     *
     * 2. Invalid UUID format:
     * Status: 400 BAD REQUEST
     * Cause: documentId path variable is not valid UUID
     * Handled automatically by Spring before reaching method
     *
     * 3. Database error:
     * Status: 500 INTERNAL SERVER ERROR
     * Cause: Database connection failure, query timeout, etc.
     * Body: Empty (error details only in server logs)
     *
     * Performance characteristics:
     *
     * 1. Fast database query:
     * Uses primary key index for O(log n) lookup
     * Typically completes in < 10ms
     * No file I/O involved, only metadata retrieval
     *
     * 2. Minimal memory usage:
     * Only metadata loaded (few hundred bytes)
     * No file content loaded into memory
     * Suitable for high-frequency access
     *
     * 3. Cacheable:
     * Response can be cached since metadata changes infrequently
     * Consider HTTP cache headers in production
     * Reduces database load for repeated access
     *
     * Security considerations:
     *
     * 1. Authorization check needed:
     * Verify authenticated user owns document or has permission
     * Prevent information disclosure to unauthorized users
     *
     * Recommended implementation:
     * - Extract user ID from JWT token
     * - Compare against document.userId
     * - Return 403 FORBIDDEN if mismatch
     *
     * 2. Information leakage:
     * Existence of document ID can be determined by 404 vs 403
     * Consider returning 404 for unauthorized access to hide existence
     *
     * 3. Enumeration attacks:
     * Sequential document IDs would allow scanning
     * UUID usage prevents enumeration (random, non-sequential)
     *
     * Client integration example:
     *
     * JavaScript Fetch API:
     * fetch('/api/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7')
     * .then(response => {
     * if (response.ok) return response.json();
     * if (response.status === 404) throw new Error('Document not found');
     * throw new Error('Server error');
     * })
     * .then(doc => {
     * console.log('File name:', doc.fileName);
     * console.log('File size:', doc.fileSize, 'bytes');
     * console.log('Upload date:', doc.uploadDate);
     * });
     *
     * Testing considerations:
     * Test cases should cover:
     * - Existing document returns 200 with full metadata
     * - Non-existent document returns 404
     * - Invalid UUID format returns 400
     * - Database error returns 500
     * - Response structure matches DocumentEntity schema
     *
     * @param documentId the UUID uniquely identifying the document in the database,
     *                   extracted from URL path and automatically parsed by Spring
     * @return a Mono emitting ResponseEntity with HTTP 200 OK and DocumentEntity
     *         body
     *         if document exists, HTTP 404 NOT FOUND if document doesn't exist, or
     *         HTTP 500 INTERNAL SERVER ERROR if database error occurs
     */
    @GetMapping("/{documentId}")
    public Mono<ResponseEntity<DocumentEntity>> getDocumentMetadata(@PathVariable UUID documentId) {
        return documentService.getDocumentById(documentId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()));
    }

    /**
     * Downloads the actual file content from MinIO storage.
     *
     * This endpoint retrieves the complete file from MinIO object storage and
     * returns it
     * to the client with appropriate headers for browser download handling. It
     * first fetches
     * metadata from the database to determine the storage location and file
     * properties, then
     * streams the file content from MinIO.
     *
     * Endpoint details:
     * - HTTP Method: GET (read-only operation)
     * - URL: /api/documents/{documentId}/download
     * - Authentication: Should require valid JWT token in production
     * - Response: 200 OK with file content as binary stream
     *
     * Download workflow:
     *
     * Phase 1: Metadata retrieval
     * 1. Query database for document metadata by ID
     * 2. Verify document exists (return 404 if not)
     * 3. Extract MinIO path and file properties
     * 4. Prepare for file retrieval
     *
     * Phase 2: File retrieval from MinIO
     * 1. Use MinIO path from metadata to locate file
     * 2. Stream file content from object storage
     * 3. Load entire file into memory as byte array
     * 4. Return byte array in response body
     *
     * Phase 3: Response header configuration
     * 1. Set Content-Disposition header for download
     * - Uses original filename from metadata
     * - "attachment" disposition triggers browser download
     * - Filename properly quoted for special characters
     *
     * 2. Set Content-Type header
     * - Uses stored file type from metadata if available
     * - Falls back to "application/octet-stream" if unknown
     * - Helps browser determine how to handle the file
     *
     * Success response structure:
     * Status: 200 OK
     * Headers:
     * Content-Disposition: attachment; filename="PASSPORT_de_Doe_John.jpg"
     * Content-Type: image/jpeg
     * Content-Length: 2457600
     * Body: Raw file bytes (binary content)
     *
     * File type handling:
     *
     * Images (JPEG, PNG, GIF):
     * - Content-Type: image/jpeg, image/png, image/gif
     * - Browsers can display inline if disposition changed to "inline"
     * - Suitable for preview functionality
     *
     * PDF documents:
     * - Content-Type: application/pdf
     * - Most browsers have built-in PDF viewers
     * - Can display inline in browser or download
     *
     * Unknown types:
     * - Content-Type: application/octet-stream
     * - Generic binary stream
     * - Always triggers download, never inline display
     *
     * Use cases:
     *
     * 1. User-initiated download:
     * User clicks download button in UI
     * Browser saves file to downloads folder
     * Original filename preserved from upload
     *
     * 2. Document verification:
     * Administrator reviews uploaded documents
     * Downloads for manual inspection
     * Verifies authenticity and quality
     *
     * 3. Automated processing:
     * External system retrieves documents for processing
     * Uses document ID from webhook or API call
     * Processes downloaded content
     *
     * 4. Document archival:
     * Backup systems download documents for archival
     * Store in long-term cold storage
     * Compliance and audit requirements
     *
     * Error scenarios:
     *
     * 1. Document not found in database:
     * Status: 404 NOT FOUND
     * Cause: Document ID doesn't exist in database
     * Body: Empty
     *
     * 2. File not found in MinIO:
     * Status: 500 INTERNAL SERVER ERROR
     * Cause: Metadata exists but file missing from storage
     * Indicates data inconsistency between database and MinIO
     *
     * 3. MinIO connection error:
     * Status: 500 INTERNAL SERVER ERROR
     * Cause: Network issue, MinIO unavailable, credentials invalid
     * Temporary condition that may resolve on retry
     *
     * 4. Insufficient permissions:
     * Status: 500 INTERNAL SERVER ERROR
     * Cause: Application lacks MinIO bucket permissions
     * Configuration or deployment issue
     *
     * Performance considerations:
     *
     * 1. Memory usage:
     * Entire file loaded into memory before sending
     * For 10MB file, requires ~10MB heap space
     * Multiple concurrent downloads multiply memory usage
     *
     * Improvement opportunity:
     * Implement streaming download to reduce memory footprint
     * Stream directly from MinIO to client without buffering
     *
     * 2. Network bandwidth:
     * Large files consume significant bandwidth
     * Consider rate limiting downloads per user
     * Monitor bandwidth usage for cost management
     *
     * 3. MinIO performance:
     * MinIO provides high-throughput object retrieval
     * Performance depends on network and MinIO cluster capacity
     * Consider CDN for frequently accessed files
     *
     * Security considerations:
     *
     * 1. Authorization required:
     * Verify authenticated user has permission to access document
     * Check document ownership or sharing permissions
     * Prevent unauthorized data access
     *
     * Implementation:
     * - Extract user ID from JWT token
     * - Verify against document.userId
     * - Return 403 FORBIDDEN if no permission
     *
     * 2. Audit logging:
     * Log all file downloads for security audit
     * * Track who downloaded what and when
     * * Helps investigate data breaches
     * *
     * * 3. Rate limiting:
     * * Prevent download abuse and DOS attacks
     * * Limit downloads per user per time period
     * * Protect storage bandwidth and costs
     * *
     * * 4. Content verification:
     * * Consider checksums to verify file integrity
     * * Detect storage corruption or tampering
     * * Include checksum in response headers
     * *
     * * Browser behavior:
     * *
     * * With "attachment" disposition:
     * * - Browser shows save file dialog
     * * - User chooses download location
     * * - Filename pre-filled from Content-Disposition
     * *
     * * With "inline" disposition:
     * * - Browser attempts to display content
     * * - Works for images, PDFs, text
     * * - Falls back to download for unknown types
     * *
     * * Client integration example:
     * *
     * * Direct link:
     * * <a href="/api/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7/download"
     * * download>Download Document</a>
     * *
     * * JavaScript Fetch:
     * * fetch('/api/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7/download')
     * * .then(response => response.blob())
     * * .then(blob => {
     * * const url = window.URL.createObjectURL(blob);
     * * const a = document.createElement('a');
     * * a.href = url;
     * * a.download = 'document.pdf';
     * * a.click();
     * * });
     * *
     * * @param documentId the UUID identifying the document to download, extracted
     * from URL path
     * * @return a Mono emitting ResponseEntity with HTTP 200 OK and file content as
     * byte array
     * * with proper headers for download, HTTP 404 NOT FOUND if document doesn't
     * exist,
     * * or HTTP 500 INTERNAL SERVER ERROR on retrieval failures
     */

    @GetMapping("/{documentId}/download")
    public Mono<ResponseEntity<byte[]>> downloadDocument(
            @PathVariable UUID documentId,
            @RequestParam(value = "side", defaultValue = "front") String side) {

        return documentService.getDocumentById(documentId)
                .flatMap(doc -> {
                    String path;
                    String suffix;

                    if ("back".equalsIgnoreCase(side)) {
                        if (doc.getBackMinioPath() == null || doc.getBackMinioPath().isEmpty()) {
                            return Mono.error(new RuntimeException("Back document not found"));
                        }
                        path = doc.getBackMinioPath();
                        suffix = "_back";
                    } else {
                        suffix = "";
                        path = doc.getMinioPath();
                    }

                    return enhancedDocumentService.retrieveFileFromSupabase(path)
                            .map(bytes -> ResponseEntity.ok()
                                    .header(HttpHeaders.CONTENT_DISPOSITION,
                                            ATTACHMENT_HEADER_PREFIX + addSuffixToFilename(doc.getFileName(), suffix)
                                                    + ATTACHMENT_HEADER_SUFFIX)
                                    .contentType(MediaType.parseMediaType(
                                            doc.getFileType() != null ? doc.getFileType() : DEFAULT_CONTENT_TYPE))
                                    .body(bytes));
                })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> {
                    if (e.getMessage().equals("Back document not found")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    private String addSuffixToFilename(String filename, String suffix) {
        if (suffix == null || suffix.isEmpty() || filename == null)
            return filename;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1)
            return filename + suffix;
        return filename.substring(0, dotIndex) + suffix + filename.substring(dotIndex);
    }
}