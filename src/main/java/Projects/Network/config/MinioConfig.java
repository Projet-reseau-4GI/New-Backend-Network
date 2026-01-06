package Projects.Network.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for MinIO object storage integration.
 *
 * This configuration class is responsible for creating and configuring the MinioClient
 * that will be used throughout the application for all object storage operations. MinIO
 * is an S3-compatible object storage system that provides high-performance file storage
 * capabilities for applications.
 *
 * The class reads connection parameters from Spring's application.properties file using
 * the @Value annotation and uses them to construct a properly configured MinioClient instance.
 * This client is then exposed as a Spring Bean, making it available for dependency injection
 * into any service or component that needs to perform file storage operations.
 *
 * Configuration properties required in application.properties:
 * - minio.url: The HTTP/HTTPS endpoint URL of the MinIO server (e.g., http://localhost:9000)
 * - minio.access-key: The access key ID for authentication (similar to AWS access key)
 * - minio.secret-key: The secret access key for authentication (similar to AWS secret key)
 * - minio.bucket-name: The name of the default bucket to use for file operations
 *
 * Architecture pattern:
 * This class follows the Spring configuration pattern where external resource clients
 * are created as singleton beans. The MinioClient is thread-safe and should be reused
 * across the application rather than creating new instances for each operation.
 *
 * Security considerations:
 * - Access and secret keys should never be hardcoded or committed to version control
 * - Use environment variables or secure configuration management for credentials
 * - The MinioClient manages connection pooling internally for optimal performance
 * - All communication with MinIO should use HTTPS in production environments
 *
 * Usage example:
 * Any service can inject the MinioClient to perform file operations:
 * @RequiredArgsConstructor
 * public class DocumentService {
 *     private final MinioClient minioClient;
 *     // Use minioClient for upload, download, delete operations
 * }
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@Configuration
public class MinioConfig {

    // Property keys as constants to avoid magic strings and enable refactoring
    private static final String MINIO_URL_PROPERTY = "${minio.url}";
    private static final String MINIO_ACCESS_KEY_PROPERTY = "${minio.access-key}";
    private static final String MINIO_SECRET_KEY_PROPERTY = "${minio.secret-key}";
    private static final String MINIO_BUCKET_NAME_PROPERTY = "${minio.bucket-name}";

    /**
     * The HTTP/HTTPS endpoint URL of the MinIO server.
     *
     * This URL specifies where the MinIO server is running and should include
     * the protocol (http:// or https://) and port number if non-standard.
     *
     * Examples:
     * - Local development: http://localhost:9000
     * - Production: https://minio.example.com:9000
     * - Docker network: http://minio:9000
     *
     * The URL must be accessible from the application's network environment.
     * In containerized deployments, ensure proper network configuration between
     * the application and MinIO containers.
     */
    @Value(MINIO_URL_PROPERTY)
    private String url;

    /**
     * The access key ID for MinIO authentication.
     *
     * This is the username-like credential used to authenticate with MinIO.
     * It works similar to AWS IAM access key IDs and identifies the principal
     * making the request to MinIO.
     *
     * Security best practices:
     * - Never hardcode this value in the source code
     * - Use environment-specific configuration files or environment variables
     * - In production, use secure secret management systems (e.g., HashiCorp Vault, AWS Secrets Manager)
     * - Rotate access keys periodically
     * - Grant minimal necessary permissions to the access key
     */
    @Value(MINIO_ACCESS_KEY_PROPERTY)
    private String accessKey;

    /**
     * The secret access key for MinIO authentication.
     *
     * This is the password-like credential that must be kept confidential.
     * It is used in combination with the access key to create authenticated
     * requests to MinIO using HMAC-SHA256 signatures.
     *
     * CRITICAL SECURITY REQUIREMENTS:
     * - NEVER commit this value to version control systems
     * - NEVER log or expose this value in application outputs
     * - Store securely using environment variables or secret management systems
     * - Use different keys for development, staging, and production environments
     * - Implement key rotation policies for production systems
     * - Ensure keys are transmitted only over encrypted channels (HTTPS/TLS)
     */
    @Value(MINIO_SECRET_KEY_PROPERTY)
    private String secretKey;

    /**
     * The name of the MinIO bucket to use for file storage operations.
     *
     * A bucket is a container for storing objects (files) in MinIO, similar to
     * folders but at the top level of the storage hierarchy. Bucket names must:
     * - Be unique within the MinIO instance
     * - Follow DNS naming conventions (lowercase, no underscores)
     * - Be between 3 and 63 characters long
     * - Not contain uppercase characters or special characters except hyphens
     *
     * Examples: "documents", "user-uploads", "application-files"
     *
     * Note: The bucket should be created before the application starts, either
     * manually through MinIO console or programmatically during application initialization.
     * Some applications may need multiple buckets for different types of content.
     */
    @Value(MINIO_BUCKET_NAME_PROPERTY)
    private String bucketName;

    /**
     * Creates and configures the MinioClient bean for dependency injection.
     *
     * This method uses the Builder pattern provided by the MinIO SDK to construct
     * a fully configured client instance. The builder ensures that all required
     * configuration parameters are properly set before the client is created.
     *
     * The MinioClient instance created by this method is:
     * - Thread-safe: Can be safely used by multiple threads concurrently
     * - Singleton: Only one instance is created and shared across the application
     * - Reusable: Maintains an internal connection pool for efficient HTTP communication
     * - Stateless: Each operation is independent and doesn't maintain session state
     *
     * Configuration steps performed:
     * 1. Set the endpoint URL where MinIO server is accessible
     * 2. Set credentials (access key and secret key) for authentication
     * 3. Build the final MinioClient instance with all configurations applied
     *
     * Connection management:
     * The MinioClient internally uses OkHttp client which manages:
     * - Connection pooling for performance optimization
     * - Automatic retry logic for transient failures
     * - Timeout configurations for network operations
     * - Keep-alive connections to reduce latency
     *
     * Error scenarios:
     * - If any required configuration is missing, the builder will throw IllegalArgumentException
     * - Network connectivity issues will only surface when actual operations are attempted
     * - Invalid credentials will result in authentication errors during operations
     *
     * Performance considerations:
     * - The client maintains persistent HTTP connections for better performance
     * - Default timeouts are suitable for most use cases but can be customized if needed
     * - For very high-throughput scenarios, consider adjusting connection pool sizes
     *
     * @return a fully configured MinioClient instance ready for performing object storage operations
     *         including upload, download, delete, list, and bucket management operations
     * @throws IllegalArgumentException if endpoint URL is malformed or credentials are invalid format
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * Exposes the MinIO bucket name as a Spring Bean for dependency injection.
     *
     * This method makes the bucket name available as a Spring-managed bean so that
     * it can be injected into services and components without repeatedly using @Value
     * annotations. This provides several benefits:
     *
     * Benefits of bean exposure:
     * - Centralized configuration: Single source of truth for the bucket name
     * - Easier testing: Can be mocked or overridden in test configurations
     * - Type safety: Compile-time checking instead of runtime string resolution
     * - Cleaner code: Avoids cluttering service classes with @Value annotations
     * - Flexibility: Can be easily changed or enhanced with additional logic if needed
     *
     * Usage in dependent classes:
     * Instead of:
     *   @Value("${minio.bucket-name}")
     *   private String bucketName;
     *
     * Use:
     *   private final String bucketName; // Injected via constructor
     *
     * This follows the principle of dependency injection and makes the code more
     * testable and maintainable. The bucket name can be injected alongside other
     * dependencies like MinioClient in service constructors.
     *
     * Design consideration:
     * While this creates an additional bean, the memory overhead is negligible
     * (a single String reference) and the benefits in code clarity and testability
     * far outweigh this minor cost.
     *
     * @return the configured bucket name from application.properties that will be
     *         used as the default storage location for all file operations
     */
    @Bean
    public String minioBucketName() {
        return bucketName;
    }
}