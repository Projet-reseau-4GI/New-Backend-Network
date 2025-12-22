package Projects.Network.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for MinIO Storage integration.
 * This class reads properties from application.properties and initializes
 * the MinioClient as a Spring Bean for the entire application to use.
 */
@Configuration
public class MinioConfig {

    // @Value pulls data from your application.properties or environment variables

    @Value("${minio.url}")
    private String url;                 // The server address (e.g., http://127.0.0.1:9000)

    @Value("${minio.access-key}")
    private String accessKey;           // Similar to a username for MinIO

    @Value("${minio.secret-key}")
    private String secretKey;           // Similar to a password for MinIO

    @Value("${minio.bucket-name}")
    private String bucketName;          // The specific 'folder' or container for your files

    /**
     * Creates and configures the MinioClient bean.
     * Spring will manage this object's lifecycle and inject it into
     * services like DocumentService when needed.
     *
     * @return A fully initialized MinioClient to perform file operations.
     */
    @Bean
    public MinioClient minioClient() {
        /* The Builder pattern is used here to securely set the
           connection details and credentials.
        */
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Exposes the bucket name as a managed String bean.
     * This allows other components to receive the bucket name
     * without having to use @Value repeatedly.
     *
     * @return The name of the target bucket defined in configuration.
     */
    @Bean
    public String minioBucketName() {
        return bucketName;
    }
}