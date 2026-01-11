package Projects.Network.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EnhancedDocumentService {

    private final MinioClient minioClient;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${parsing.api.url:https://b860jci1i6q6e1s2.aistudio-app.com/layout-parsing}")
    private String parsingApiUrl;

    @Value("${parsing.api.token:80217f3d365a319e8a2b20c83639f4e0468a7d05}")
    private String parsingApiToken;

    public Mono<byte[]> retrieveFileFromMinio(String objectName) {
        return Mono.fromCallable(() -> {
            System.out.println("📥 Retrieving: " + objectName);
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                byte[] bytes = out.toByteArray();
                System.out.println("✓ Retrieved: " + String.format("%.2f MB", bytes.length / (1024.0 * 1024.0)));
                return bytes;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Map<String, Object>> sendToParsingApi(byte[] fileBytes, int fileType) {
        return Mono.fromCallable(() -> {
                    System.out.println("🔐 Encoding to Base64...");
                    return Base64.getEncoder().encodeToString(fileBytes);
                })
                .flatMap(base64File -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("file", base64File);
                    payload.put("fileType", fileType);
                    payload.put("useDocOrientationClassify", false);
                    payload.put("useDocUnwarping", false);
                    payload.put("useChartRecognition", false);

                    // Configuration HTTP avec timeouts prolongés
                    HttpClient httpClient = HttpClient.create()
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000) // 60s connexion
                            .responseTimeout(Duration.ofMinutes(10)) // 10min réponse
                            .secure(spec -> {
                                try {
                                    spec.sslContext(SslContextBuilder.forClient().build())
                                        .handlerConfigurator(handler -> handler.setHandshakeTimeout(120, TimeUnit.SECONDS));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .doOnConnected(conn -> 
                                conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.MINUTES))
                                    .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.MINUTES))
                            );

                    WebClient client = webClientBuilder
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .build();

                    System.out.println("📡 Sending to API (this may take 5-10 minutes)...");
                    long start = System.currentTimeMillis();

                    return client.post()
                            .uri(parsingApiUrl)
                            .header("Authorization", "token " + parsingApiToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofMinutes(10))
                            .map(response -> {
                                long elapsed = (System.currentTimeMillis() - start) / 1000;
                                System.out.println("✅ Response received (" + elapsed + "s)");
                                
                                if (response == null || response.get("result") == null) {
                                    throw new RuntimeException("Invalid API response");
                                }
                                
                                return (Map<String, Object>) response.get("result");
                            })
                            .doOnError(e -> 
                                System.err.println("❌ API Error: " + e.getMessage())
                            );
                });
    }

    private int determineFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return 0;
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) return 1;
        throw new IllegalArgumentException("Unsupported: " + filename);
    }

    public Mono<Map<String, Object>> retrieveAndParseDocument(String objectName) {
        System.out.println("\n🚀 Starting parsing: " + objectName);
        return retrieveFileFromMinio(objectName)
                .flatMap(bytes -> sendToParsingApi(bytes, determineFileType(objectName)))
                .doOnSuccess(r -> System.out.println("✓ Parsing complete"))
                .doOnError(e -> System.err.println("✗ Parsing failed: " + e.getMessage()));
    }

    public Mono<String> extractMarkdownText(String objectName) {
        return retrieveAndParseDocument(objectName)
                .map(result -> {
                    try {
                        var layouts = (java.util.List<?>) result.get("layoutParsingResults");
                        if (layouts == null || layouts.isEmpty()) return "No results";
                        
                        var first = (Map<String, Object>) layouts.get(0);
                        var markdown = (Map<String, Object>) first.get("markdown");
                        if (markdown == null) return "No markdown";
                        
                        String text = (String) markdown.get("text");
                        System.out.println("📝 Extracted: " + (text != null ? text.length() : 0) + " chars");
                        return text != null ? text : "No text";
                        
                    } catch (Exception e) {
                        throw new RuntimeException("Extraction error: " + e.getMessage());
                    }
                });
    }

    // Dans EnhancedDocumentService
@PreDestroy
public void cleanup() {
    System.out.println("🧹 Cleaning up HTTP connections...");
}
}