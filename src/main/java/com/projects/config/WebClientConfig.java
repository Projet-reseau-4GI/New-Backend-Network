package com.projects.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration class for WebClient used in external API communications.
 *
 * This configuration class creates and configures WebClient instances that are used
 * throughout the application for making HTTP requests to external services and APIs.
 * WebClient is Spring's reactive, non-blocking HTTP client designed to work seamlessly
 * with WebFlux applications and reactive programming patterns.
 *
 * Primary use case in this application:
 * The configured WebClient is primarily used by EnhancedDocumentService to communicate
 * with external document parsing APIs. These APIs accept document files encoded in Base64
 * format and return parsed content or extracted text. The parsing operations can be
 * time-consuming, especially for large documents, which is why extended timeouts are
 * configured.
 *
 * Key configuration requirements:
 *
 * 1. Extended timeout values (120 seconds):
 *    Document parsing APIs can take significant time to process files, particularly:
 *    - Large PDF documents with many pages
 *    - High-resolution image files requiring OCR processing
 *    - Complex document layouts requiring AI-based parsing
 *    The 120-second timeout balances user experience with processing needs.
 *
 * 2. Increased memory buffer size (10MB):
 *    Document files sent to parsing APIs are Base64-encoded, which increases size by ~33%.
 *    A 7MB original file becomes ~9.3MB when Base64-encoded. The 10MB buffer ensures
 *    the entire request/response can be held in memory without streaming, which simplifies
 *    error handling and retry logic.
 *
 * 3. Reactive/non-blocking architecture:
 *    WebClient integrates with Project Reactor, allowing the application to make
 *    concurrent API calls without blocking threads. This is crucial for scalability
 *    when processing multiple document uploads simultaneously.
 *
 * Architecture pattern:
 * This configuration follows the Builder pattern, exposing a WebClient.Builder bean
 * rather than a fully constructed WebClient. This allows different parts of the application
 * to create customized WebClient instances with additional configuration while still
 * inheriting the base timeout and buffer settings defined here.
 *
 * Usage example in services:
 * @RequiredArgsConstructor
 * public class EnhancedDocumentService {
 *     private final WebClient.Builder webClientBuilder;
 *
 *     public Mono<String> callExternalApi(String url) {
 *         WebClient client = webClientBuilder.baseUrl(url).build();
 *         return client.post()...
 *     }
 * }
 *
 * Network resilience:
 * While this configuration sets timeouts, additional resilience patterns should be
 * considered for production:
 * - Retry logic with exponential backoff for transient failures
 * - Circuit breaker pattern to prevent cascading failures
 * - Fallback mechanisms when external APIs are unavailable
 * - Request hedging for critical operations
 *
 * Performance monitoring:
 * Consider implementing metrics for:
 * - Average response times from external APIs
 * - Timeout occurrence rate
 * - Success/failure ratio
 * - Queue depth if requests are buffered
 *
 * Security considerations:
 * - All external API calls should use HTTPS in production
 * - API keys and tokens should be injected via configuration, never hardcoded
 * - Implement request signing if required by external APIs
 * - Validate SSL certificates (don't disable certificate verification)
 * - Consider IP whitelisting for sensitive API endpoints
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@Configuration
public class WebClientConfig {

    /**
     * Timeout duration in seconds for all network operations.
     *
     * This constant defines the maximum time allowed for:
     * - Establishing a connection to the remote server (connection timeout)
     * - Reading response data from the server (read timeout)
     * - Writing request data to the server (write timeout)
     * - Overall response completion (response timeout)
     *
     * Value rationale (120 seconds):
     * The 120-second timeout is specifically chosen to accommodate document parsing operations
     * which can be computationally expensive. Typical processing times:
     * - Small images (< 1MB): 5-15 seconds
     * - Large images (5-10MB): 30-60 seconds
     * - Multi-page PDFs: 60-90 seconds
     * - Complex documents with tables/charts: 90-120 seconds
     *
     * The timeout should be set based on the 95th percentile of expected response times
     * to avoid premature timeout failures for legitimate requests while still catching
     * truly hung connections.
     *
     * Adjustment considerations:
     * - If timeout errors are frequent, consider increasing this value
     * - If external API SLAs change, adjust accordingly
     * - Monitor actual response times to optimize this value
     * - Different APIs may need different timeout values (use multiple WebClient configs)
     */
    private static final int TIMEOUT_SECONDS = 120;

    /**
     * Maximum in-memory buffer size for request and response bodies.
     *
     * This constant defines the maximum size of HTTP message content that can be buffered
     * in memory before the application needs to switch to streaming mode. The value is
     * specified in bytes: 10 * 1024 * 1024 = 10,485,760 bytes = 10 megabytes.
     *
     * Why 10MB is chosen:
     *
     * 1. Base64 encoding overhead:
     *    Original file size grows by approximately 33% when Base64-encoded.
     *    Example: A 7MB original file becomes ~9.3MB when encoded.
     *    The 10MB limit accommodates files up to ~7.5MB before encoding.
     *
     * 2. Memory management:
     *    Each concurrent request consumes buffer space in heap memory.
     *    With 10MB buffers and adequate heap size, the application can handle
     *    multiple simultaneous document uploads without memory pressure.
     *
     *    Example calculation for 2GB heap:
     *    - JVM overhead: ~500MB
     *    - Application base memory: ~500MB
     *    - Available for buffers: ~1GB
     *    - Concurrent requests supported: 1GB / 10MB = ~100 requests
     *
     * 3. Response size considerations:
     *    The parsing API responses contain JSON with document structure and text.
     *    For very large documents, responses can be several megabytes.
     *    The 10MB limit ensures responses can be fully buffered as well.
     *
     * Alternative approach for larger files:
     * If files larger than 10MB need to be supported, consider:
     * - Increasing heap size and this buffer limit (not recommended beyond 50MB)
     * - Implementing streaming upload/download instead of buffering
     * - Compressing files before sending to external APIs
     * - Splitting large documents into smaller chunks for processing
     * - Using temporary disk storage instead of memory buffers
     *
     * Memory pressure symptoms:
     * If this buffer is too large relative to heap size, watch for:
     * - OutOfMemoryError during high load
     * - Frequent full garbage collections
     * - Increased response latency due to GC pauses
     * - Application becomes unresponsive under concurrent load
     *
     * Performance trade-offs:
     * Larger buffers:
     * - Pro: Can handle larger files without streaming complexity
     * - Pro: Simpler error handling (entire request/response in memory)
     * - Con: Higher memory usage limits concurrency
     * - Con: Longer GC pauses when buffers are released
     *
     * Smaller buffers:
     * - Pro: Lower memory footprint allows more concurrent requests
     * - Pro: More predictable memory usage patterns
     * - Con: Must implement streaming for larger files (added complexity)
     * - Con: Partial request/response handling needed
     */
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;

    /**
     * Creates and configures a WebClient.Builder bean with custom timeout and buffer settings.
     *
     * This method constructs a WebClient.Builder that serves as a template for creating
     * WebClient instances throughout the application. The builder pattern allows services
     * to inherit these base configurations while adding their own customizations like
     * base URLs, default headers, or filters.
     *
     * Configuration layers explained:
     *
     * Layer 1 - HttpClient (Netty-based):
     * The underlying HTTP client is built on Project Reactor Netty, which provides
     * non-blocking I/O operations. This is configured with:
     *
     * a) Response timeout (Duration.ofSeconds(120)):
     *    This sets the maximum time allowed for the entire HTTP request-response cycle,
     *    from the moment the request is sent until the complete response is received.
     *    This is implemented at the HTTP protocol level and applies to the full transaction.
     *
     *    Technical implementation:
     *    - Starts when the request begins sending
     *    - Includes connection time, data transmission time, server processing time
     *    - Triggers timeout if the response is not fully received within the duration
     *    - Results in TimeoutException that can be handled reactively
     *
     * b) Connection event handlers (doOnConnected):
     *    These handlers are invoked when a TCP connection is successfully established
     *    to the remote server. They add Netty pipeline handlers that monitor data flow:
     *
     *    - ReadTimeoutHandler (120 seconds):
     *      Monitors inbound data flow from server to client.
     *      Triggers if no data is received for 120 consecutive seconds.
     *      This catches scenarios where the server stops sending data mid-response,
     *      such as server hangs, network partitions, or server-side processing failures.
     *
     *      Technical detail: This is implemented using Netty's IdleStateHandler
     *      which monitors channel activity at the TCP level.
     *
     *    - WriteTimeoutHandler (120 seconds):
     *      Monitors outbound data flow from client to server.
     *      Triggers if data cannot be written to the socket for 120 consecutive seconds.
     *      This catches scenarios like slow server acceptance, network congestion,
     *      or backpressure from the server side.
     *
     *      Technical detail: Critical for large request bodies (Base64-encoded files)
     *      that may take time to transmit, especially on slower networks.
     *
     * Why three separate timeout mechanisms:
     * These timeouts serve different purposes and catch different failure modes:
     * - Response timeout: Overall operation deadline (business logic timeout)
     * - Read timeout: Detects server hanging or network issues during response
     * - Write timeout: Detects network issues or server backpressure during request
     *
     * Having all three ensures robust failure detection across all phases of the
     * HTTP request lifecycle.
     *
     * Layer 2 - ReactorClientHttpConnector:
     * This connector bridges between Spring's WebClient API and the Reactor Netty
     * HttpClient. It translates WebClient's high-level operations into Netty's
     * low-level network operations while preserving reactive semantics.
     *
     * Layer 3 - WebClient.Builder:
     * The WebClient.Builder provides the final configuration layer where we set:
     *
     * a) clientConnector:
     *    Associates the configured HttpClient with this WebClient builder.
     *    All WebClient instances created from this builder will use the same
     *    underlying HTTP client configuration (timeouts, connection pooling, etc.)
     *
     * b) codecs (maxInMemorySize):
     *    Configures how HTTP message bodies are encoded and decoded.
     *    The maxInMemorySize setting determines the buffer size for:
     *    - Request body buffering (for retries, logging, or transformations)
     *    - Response body buffering (before deserialization to Java objects)
     *
     *    When content exceeds this size:
     *    - WebClient switches to streaming mode automatically
     *    - Data is processed in chunks rather than loaded fully into memory
     *    - May limit ability to retry requests or log full bodies
     *
     *    The 10MB limit balances:
     *    - Memory efficiency (prevents OutOfMemoryError)
     *    - Functionality (can buffer typical document sizes)
     *    - Performance (avoids excessive GC from large allocations)
     *
     * Connection pooling behavior:
     * The underlying Reactor Netty client maintains a connection pool for efficiency:
     * - Connections are reused across multiple requests to the same host
     * - Default pool size is based on available CPU cores
     * - Idle connections are kept alive for a default period
     * - Pool size and behavior can be customized if needed via HttpClient configuration
     *
     * Thread model:
     * WebClient operations are non-blocking and use the Reactor event loop:
     * - No dedicated thread per request (unlike RestTemplate)
     * - Callbacks are executed on Reactor worker threads
     * - Application code should avoid blocking operations in WebClient chains
     * - For blocking operations, use .publishOn(Schedulers.boundedElastic())
     *
     * Error handling:
     * Timeout errors manifest as:
     * - java.util.concurrent.TimeoutException (response timeout)
     * - io.netty.handler.timeout.ReadTimeoutException (read timeout)
     * - io.netty.handler.timeout.WriteTimeoutException (write timeout)
     *
     * These can be handled using WebClient's onErrorResume, onErrorMap, or retry operators.
     *
     * Testing considerations:
     * When testing code that uses this WebClient:
     * - Use WireMock or similar tools to mock external APIs
     * - Test timeout scenarios by simulating slow responses
     * - Verify buffer limits by sending large payloads
     * - Monitor memory usage during load tests
     *
     * Production deployment checklist:
     * Before using in production, ensure:
     * - Heap size is adequate for expected concurrent request volume
     * - Monitoring is in place for timeout frequency
     * - Circuit breakers wrap external API calls
     * - Timeouts align with external API SLAs
     * - Fallback strategies exist for when external APIs are unavailable
     *
     * @return a configured WebClient.Builder that can be injected into services
     *         and used to create WebClient instances with consistent timeout
     *         and buffer configurations suitable for document processing operations
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(TIMEOUT_SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(TIMEOUT_SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES));
    }
}