package Projects.Network.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;

/**
 * Technical service dedicated to low-level MinIO operations.
 * This service focuses solely on file storage, separating storage logic
 * from business logic found in DocumentService.
 */
@Service
@RequiredArgsConstructor // Automatically generates a constructor for final fields (minioClient)
public class MinioService {

    // The core MinIO client initialized in MinioConfig
    private final MinioClient minioClient;

    // The target bucket name retrieved from the application properties file
    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Uploads raw bytes to a specific MinIO bucket.
     * This method wraps a blocking MinIO call into a non-blocking Mono.
     * * @param objectName  The unique destination name/path of the file in the bucket.
     * @param content     The file content as a byte array.
     * @param contentType The MIME type of the file (e.g., image/png, application/pdf).
     * @return An empty Mono<Void> that completes when the upload is finished.
     */
    public Mono<Void> uploadFile(String objectName, byte[] content, String contentType) {
        /* Mono.fromRunnable is used here to wrap the synchronous MinIO 'putObject'
           call into a reactive stream.
        */
        return Mono.fromRunnable(() -> {
            try {
                // Building the upload request with specific stream parameters
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .contentType(contentType)
                        .build());
            } catch (Exception e) {
                // Wrapping checked exceptions into a RuntimeException for reactive error handling
                throw new RuntimeException("Error during MinIO upload: " + e.getMessage());
            }
        });
    }
}