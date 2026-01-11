package Projects.Network.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * MinioService - Adapted for reactive file upload
 *
 * Handles MinIO storage operations with support for:
 * - Raw byte array upload
 * - Reactive FilePart upload (WebFlux multipart)
 *
 * @author Thomas Djotio Ndié
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Upload raw byte content to MinIO
     */
    public Mono<Void> uploadFile(String objectName, byte[] content, String contentType) {
        return Mono.fromRunnable(() -> {
            try {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(new ByteArrayInputStream(content), content.length, -1)
                                .contentType(contentType)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException("MinIO upload error: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Upload reactive FilePart to MinIO
     *
     * @param filePart reactive multipart file from WebFlux
     * @param objectPath target path in MinIO bucket
     * @return Mono<String> URL or path of uploaded file
     */
    public Mono<String> uploadFile(FilePart filePart, String objectPath) {
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        // Detect content type
                        String contentType = detectContentType(filePart.filename());

                        // Upload to MinIO
                        minioClient.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectPath)
                                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                                        .contentType(contentType)
                                        .build()
                        );

                        return Mono.just(objectPath);

                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("MinIO upload failed: " + e.getMessage(), e));
                    }
                });
    }

    /**
     * Detect content type from filename
     */
    private String detectContentType(String filename) {
        if (filename == null) return "application/octet-stream";

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";

        return "application/octet-stream";
    }
}