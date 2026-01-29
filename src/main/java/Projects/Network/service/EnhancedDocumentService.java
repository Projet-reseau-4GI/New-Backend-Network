package Projects.Network.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * EnhancedDocumentService - Service de récupération et parsing de documents
 * depuis Supabase
 *
 * Gère les opérations de:
 * - Récupération de fichiers depuis Supabase Storage
 * - Envoi vers l'API de parsing avec timeouts étendus
 * - Extraction de markdown depuis les résultats
 *
 * @author Thomas Djotio Ndié
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
public class EnhancedDocumentService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final SupabaseStorageService supabaseStorageService;
    private WebClient webClient;

    @Value("${parsing.api.url:https://b860jci1i6q6e1s2.aistudio-app.com/layout-parsing}")
    private String parsingApiUrl;

    @Value("${parsing.api.token:80217f3d365a319e8a2b20c83639f4e0468a7d05}")
    private String parsingApiToken;

    @jakarta.annotation.PostConstruct
    public void init() {
        // Configuration HTTP avec timeouts prolongés et pool de connexions
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000) // 60s connexion
                .responseTimeout(Duration.ofMinutes(10)) // 10min réponse
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forClient().build())
                                .handlerConfigurator(
                                        handler -> handler.setHandshakeTimeout(120, TimeUnit.SECONDS));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.MINUTES)));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                        .build())
                .build();
    }

    /**
     * Récupère un fichier depuis Supabase Storage
     *
     * @param objectName chemin du fichier dans le bucket
     * @return Mono<byte[]> contenu binaire du fichier
     */
    public Mono<byte[]> retrieveFileFromSupabase(String objectName) {
        System.out.println("📥 Retrieving from Supabase: " + objectName);
        return supabaseStorageService.downloadFile(objectName)
                .doOnSuccess(bytes -> {
                    if (bytes != null) {
                        System.out
                                .println("✓ Retrieved: " + String.format("%.2f MB", bytes.length / (1024.0 * 1024.0)));
                    }
                })
                .doOnError(e -> System.err.println("❌ Download failed: " + e.getMessage()));
    }

    /**
     * Envoie le fichier à l'API de parsing avec configuration timeout prolongée
     *
     * @param fileBytes contenu binaire du fichier
     * @param fileType  type de fichier (0=PDF, 1=Image)
     * @return Mono<Map<String, Object>> résultat du parsing
     */
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

                    System.out.println("📡 Sending to API (this may take 5-10 minutes)...");
                    long start = System.currentTimeMillis();

                    return webClient.post()
                            .uri(parsingApiUrl)
                            .header("Authorization", "token " + parsingApiToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofMinutes(10))
                            .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofSeconds(2))
                                    .filter(throwable -> throwable instanceof io.netty.handler.codec.DecoderException
                                            || throwable instanceof reactor.netty.http.client.PrematureCloseException))
                            .map(response -> {
                                long elapsed = (System.currentTimeMillis() - start) / 1000;
                                System.out.println("✅ Response received (" + elapsed + "s)");

                                if (response == null || response.get("result") == null) {
                                    throw new RuntimeException("Invalid API response");
                                }

                                return (Map<String, Object>) response.get("result");
                            })
                            .doOnError(e -> System.err.println("❌ API Error: " + e.getMessage()));
                });
    }

    /**
     * Détermine le type de fichier depuis son nom
     *
     * @param filename nom du fichier avec extension
     * @return 0 pour PDF, 1 pour images
     * @throws IllegalArgumentException si format non supporté
     */
    private int determineFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))
            return 0;
        if (lower.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|tiff|tif)$"))
            return 1;
        throw new IllegalArgumentException("Unsupported: " + filename);
    }

    /**
     * Récupère et parse un document complet depuis Supabase
     *
     * @param objectName chemin du fichier dans Supabase
     * @return Mono<Map<String, Object>> résultat complet du parsing
     */
    public Mono<Map<String, Object>> retrieveAndParseDocument(String objectName) {
        System.out.println("\n🚀 Starting parsing: " + objectName);
        return retrieveFileFromSupabase(objectName)
                .flatMap(bytes -> sendToParsingApi(bytes, determineFileType(objectName)))
                .doOnSuccess(r -> System.out.println("✓ Parsing complete"))
                .doOnError(e -> System.err.println("✗ Parsing failed: " + e.getMessage()));
    }

    /**
     * Extrait uniquement le texte markdown d'un document
     *
     * @param objectName chemin du fichier dans Supabase
     * @return Mono<String> texte markdown extrait
     */
    public Mono<String> extractMarkdownText(String objectName) {
        return retrieveAndParseDocument(objectName)
                .map(result -> {
                    try {
                        var layouts = (java.util.List<?>) result.get("layoutParsingResults");
                        if (layouts == null || layouts.isEmpty())
                            return "No results";

                        var first = (Map<String, Object>) layouts.get(0);
                        var markdown = (Map<String, Object>) first.get("markdown");
                        if (markdown == null)
                            return "No markdown";

                        String text = (String) markdown.get("text");
                        System.out.println("📝 Extracted: " + (text != null ? text.length() : 0) + " chars");
                        return text != null ? text : "No text";

                    } catch (Exception e) {
                        throw new RuntimeException("Extraction error: " + e.getMessage());
                    }
                });
    }

    /**
     * Nettoyage des connexions HTTP au shutdown
     */
    @PreDestroy
    public void cleanup() {
        System.out.println("🧹 Cleaning up HTTP connections...");
    }
}