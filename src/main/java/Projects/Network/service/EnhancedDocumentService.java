package Projects.Network.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * EnhancedDocumentService
 *
 * Service responsible for advanced document processing operations.
 *
 * This service retrieves files from MinIO object storage and sends them
 * to an external parsing API for document layout analysis and text extraction.
 *
 * The service is designed to handle large files by configuring extended
 * network timeouts and executing blocking I/O operations on a dedicated
 * bounded elastic scheduler.
 *
 * This class belongs to the Service layer and contains business logic.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Service
@RequiredArgsConstructor
public class EnhancedDocumentService {

    /**
     * MinIO client used to retrieve stored documents.
     */
    private final MinioClient minioClient;

    /**
     * WebClient builder used to configure HTTP clients dynamically.
     */
    private final WebClient.Builder webClientBuilder;

    /**
     * ObjectMapper used for JSON processing when needed.
     */
    private final ObjectMapper objectMapper;

    /**
     * Name of the MinIO bucket where documents are stored.
     */
    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * URL of the external document parsing API.
     */
    @Value("${parsing.api.url:https://b860jci1i6q6e1s2.aistudio-app.com/layout-parsing}")
    private String parsingApiUrl;

    /**
     * Authentication token used to access the parsing API.
     */
    @Value("${parsing.api.token:80217f3d365a319e8a2b20c83639f4e0468a7d05}")
    private String parsingApiToken;

    /**
     * Retrieves a file from MinIO object storage.
     *
     * This method performs a blocking I/O operation and therefore
     * executes on a bounded elastic scheduler.
     *
     * @param objectName name of the object stored in MinIO
     * @return a Mono emitting the file content as a byte array
     */
    public Mono<byte[]> retrieveFileFromMinio(String objectName) {
        return Mono.fromCallable(() -> {
            System.out.println("Retrieving file from MinIO: " + objectName);
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                byte[] fileBytes = outputStream.toByteArray();
                double fileSizeMB = fileBytes.length / (1024.0 * 1024.0);
                System.out.println("File retrieved: " + objectName + " (" +
                        String.format("%.2f", fileSizeMB) + " MB)");

                if (fileSizeMB > 10) {
                    System.err.println("Warning: File is very large (" +
                            String.format("%.2f", fileSizeMB) + " MB). API may timeout.");
                }

                return fileBytes;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Sends a file to the external parsing API.
     *
     * The file is encoded in Base64 and transmitted as JSON payload.
     * Extended network timeouts are configured to support large files
     * and long processing times.
     *
     * @param fileBytes raw file content
     * @param fileType type indicator (0 = PDF, 1 = image)
     * @return a Mono emitting the parsed result as a Map
     */
    public Mono<Map<String, Object>> sendToParsingApi(byte[] fileBytes, int fileType) {
        return Mono.fromCallable(() -> {
                    System.out.println("Encoding file to Base64...");
                    String base64File = Base64.getEncoder().encodeToString(fileBytes);
                    double base64SizeMB = base64File.length() / (1024.0 * 1024.0);
                    System.out.println("Base64 encoded size: " +
                            String.format("%.2f", base64SizeMB) + " MB");
                    return base64File;
                })
                .flatMap(base64File -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", base64File);
                    payload.put("fileType", fileType);
                    payload.put("useDocOrientationClassify", false);
                    payload.put("useDocUnwarping", false);
                    payload.put("useChartRecognition", false);

                    HttpClient httpClient = HttpClient.create()
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                            .responseTimeout(Duration.ofMinutes(5))
                            .doOnConnected(conn ->
                                    conn.addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))
                                            .addHandlerLast(new WriteTimeoutHandler(300, TimeUnit.SECONDS)));

                    WebClient webClient = webClientBuilder
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .build();

                    System.out.println("Sending request to parsing API: " + parsingApiUrl);
                    System.out.println("This operation may take several minutes.");
                    long startTime = System.currentTimeMillis();

                    return webClient.post()
                            .uri(parsingApiUrl)
                            .header("Authorization", "token " + parsingApiToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofMinutes(5))
                            .map(response -> {
                                long duration =
                                        (System.currentTimeMillis() - startTime) / 1000;
                                System.out.println("Response received after " +
                                        duration + " seconds");

                                if (response == null) {
                                    throw new RuntimeException("API returned null response");
                                }

                                Object result = response.get("result");
                                if (result == null) {
                                    throw new RuntimeException(
                                            "API response missing 'result' field");
                                }

                                return (Map<String, Object>) result;
                            })
                            .doOnError(e -> {
                                long duration =
                                        (System.currentTimeMillis() - startTime) / 1000;
                                System.err.println("Error calling parsing API after " +
                                        duration + " seconds");
                                System.err.println("Error message: " + e.getMessage());
                            });
                });
    }

    /**
     * Determines the file type based on its extension.
     *
     * @param filename name of the file
     * @return 0 for PDF files, 1 for image files
     */
    private int determineFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            System.out.println("Detected file type: PDF");
            return 0;
        } else if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
            System.out.println("Detected file type: Image");
            return 1;
        }
        throw new IllegalArgumentException(
                "Unsupported file type: " + filename);
    }

    /**
     * Complete processing pipeline: file retrieval followed by parsing.
     *
     * @param objectName name of the object stored in MinIO
     * @return a Mono emitting the parsing result
     */
    public Mono<Map<String, Object>> retrieveAndParseDocument(String objectName) {
        System.out.println("Starting document parsing pipeline");
        System.out.println("Document: " + objectName);

        return retrieveFileFromMinio(objectName)
                .flatMap(bytes -> {
                    int fileType = determineFileType(objectName);
                    return sendToParsingApi(bytes, fileType);
                })
                .doOnSuccess(result ->
                        System.out.println("Document parsed successfully: " + objectName))
                .doOnError(error -> {
                    System.err.println("Document parsing failed: " + objectName);
                    System.err.println("Error: " + error.getMessage());
                    error.printStackTrace();
                });
    }

    /**
     * Extracts the markdown text content from the parsing result.
     *
     * @param objectName name of the document stored in MinIO
     * @return a Mono emitting the extracted markdown text
     */
    public Mono<String> extractMarkdownText(String objectName) {
        return retrieveAndParseDocument(objectName)
                .map(result -> {
                    try {
                        System.out.println("Extracting markdown text from parsing result");

                        var layoutResults =
                                (java.util.List<?>) result.get("layoutParsingResults");

                        if (layoutResults == null || layoutResults.isEmpty()) {
                            return "No layout parsing results found";
                        }

                        var firstResult =
                                (Map<String, Object>) layoutResults.get(0);
                        var markdown =
                                (Map<String, Object>) firstResult.get("markdown");

                        if (markdown == null) {
                            return "No markdown content found";
                        }

                        String text = (String) markdown.get("text");
                        if (text == null || text.isEmpty()) {
                            return "No text extracted";
                        }

                        System.out.println("Markdown text extracted: " +
                                text.length() + " characters");
                        return text;

                    } catch (Exception e) {
                        System.err.println(
                                "Error extracting markdown text: " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException(
                                "Markdown extraction error: " + e.getMessage());
                    }
                });
    }
}
