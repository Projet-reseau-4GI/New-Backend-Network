package Projects.Network.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;

/**
 * MinioService
 *
 * Technical service dedicated to low-level interactions with MinIO object storage.
 *
 * This service is responsible only for file storage operations and deliberately
 * avoids any business logic. Its goal is to isolate infrastructure-related
 * concerns from higher-level services such as DocumentService.
 *
 * All operations provided by this service are wrapped in reactive types
 * in order to integrate seamlessly with a reactive application stack.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Service
@RequiredArgsConstructor
public class MinioService {

    /**
     * Core MinIO client used to communicate with the object storage server.
     *
     * This client is configured and instantiated in the Minio configuration
     * class and injected here via constructor injection.
     */
    private final MinioClient minioClient;

    /**
     * Name of the MinIO bucket where files will be stored.
     *
     * This value is injected from the application configuration properties.
     */
    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Uploads raw binary content to the configured MinIO bucket.
     *
     * This method wraps a blocking MinIO operation into a reactive Mono
     * in order to integrate with the reactive programming model.
     *
     * The upload is performed using a ByteArrayInputStream created
     * from the provided byte array.
     *
     * @param objectName the unique object name or path inside the bucket
     * @param content the binary content of the file
     * @param contentType the MIME type of the file (for example, application/pdf or image/png)
     * @return a Mono that completes when the upload operation finishes
     */
    public Mono<Void> uploadFile(String objectName, byte[] content, String contentType) {
        return Mono.fromRunnable(() -> {
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(
                                        new ByteArrayInputStream(content),
                                        content.length,
                                        -1
                                )
                                .contentType(contentType)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException(
                        "Error during MinIO upload: " + e.getMessage()
                );
            }
        });
    }
}
