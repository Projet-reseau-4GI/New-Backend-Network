package yowyob.comops.api.file.application.service;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import yowyob.comops.api.file.application.port.in.StoreFileCommand;
import yowyob.comops.api.file.application.port.out.KycVerificationPort;
import yowyob.comops.api.file.domain.KycValidationException;

import java.util.List;
import java.util.UUID;

@Service
public class KycVerificationService implements KycVerificationPort {

    private final WebClient webClient;

    public KycVerificationService(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("http://localhost:8080")
                .build();
    }

    @Override
    public Mono<Void> verify(UUID tenantId, StoreFileCommand command) {

        // On lit le contenu du fichier en bytes une seule fois
        return DataBufferUtils.join(command.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    // On construit le formulaire multipart
                    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
                    bodyBuilder.part("frontFile", bytes)
                            .filename(command.fileName())
                            .contentType(MediaType.parseMediaType(command.contentType()));

                    // On appelle le backend KYC
                    return webClient.post()
                            .uri("/api/documents/upload-analyze")
                            .header("X-API-KEY", "39f07214-3799-44fd-937b-87d8ac1f043d")
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                            .retrieve()
                            .bodyToMono(KycAnalysisResponse.class)
                            .flatMap(response -> {
                                if (!response.isValid()) {
                                    return Mono.error(new KycValidationException(
                                            List.of(response.validationMessage())
                                    ));
                                }
                                return Mono.empty();
                            });
                });
    }

    // DTO pour lire la réponse du backend KYC
    record KycAnalysisResponse(
            boolean isValid,
            String validationMessage,
            double confidenceScore
    ) {}
}
