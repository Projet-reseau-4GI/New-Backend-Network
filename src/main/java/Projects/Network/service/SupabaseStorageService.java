package Projects.Network.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class SupabaseStorageService {

    private final WebClient webClient;
    private final EncryptionService encryptionService;

    @Autowired
    public SupabaseStorageService(WebClient.Builder webClientBuilder, EncryptionService encryptionService) {
        this.webClient = webClientBuilder.build();
        this.encryptionService = encryptionService;
    }

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.bucket}")
    private String bucket;

    public Mono<String> uploadFile(String objectPath, byte[] content, String contentType) {
        return Mono.fromCallable(() -> encryptionService.encrypt(content))
                .flatMap(encrypted -> webClient.put()
                        .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                        .header("apikey", serviceRoleKey)
                        .header("x-upsert", "true")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .bodyValue(encrypted)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .thenReturn(objectPath));
    }

    public Mono<String> uploadFile(FilePart filePart, String objectPath) {
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return uploadFile(objectPath, bytes, filePart.headers().getContentType().toString());
                });
    }

    public Mono<byte[]> downloadAndDecryptFile(String objectPath) {
        return webClient.get()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(encryptionService::decrypt);
    }

    public Mono<Void> deleteFile(String objectPath) {
        return webClient.delete()
                .uri(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .bodyToMono(Void.class);
    }
}