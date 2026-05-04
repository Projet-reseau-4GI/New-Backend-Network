package yowyob.comops.api.file.adapter.in.web;

import yowyob.comops.api.common.domain.model.ApiResponse;
import yowyob.comops.api.file.application.port.in.GetStoredFileUseCase;
import yowyob.comops.api.file.application.port.in.StoreFileCommand;
import yowyob.comops.api.file.application.port.in.StoreFileUseCase;
import yowyob.comops.api.file.domain.model.StoredFile;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/files")
@PreAuthorize("@businessAccessPolicy.hasUserContext(authentication)")
public class FileController {

    private final StoreFileUseCase storeFileUseCase;
    private final GetStoredFileUseCase getStoredFileUseCase;

    public FileController(StoreFileUseCase storeFileUseCase, GetStoredFileUseCase getStoredFileUseCase) {
        this.storeFileUseCase = storeFileUseCase;
        this.getStoredFileUseCase = getStoredFileUseCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ApiResponse<StoredFileResponse>>> uploadFile(@RequestPart("file") Mono<FilePart> fileMono) {
        return fileMono.flatMap(file -> storeFileUseCase.store(new StoreFileCommand(
                        file.filename(),
                        file.headers().getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                                : file.headers().getContentType().toString(),
                        file.headers().getContentLength(),
                        file.content())))
                .map(StoredFileResponse::from)
                .map(response -> ResponseEntity.status(201).body(ApiResponse.success(response, "File uploaded.")));
    }

    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<Resource>> downloadFile(@PathVariable UUID fileId) {
        return getStoredFileUseCase.getMetadata(fileId)
                .zipWith(getStoredFileUseCase.loadResource(fileId))
                .map(tuple -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + tuple.getT1().fileName() + "\"")
                        .contentType(MediaType.parseMediaType(tuple.getT1().contentType()))
                        .body(tuple.getT2()));
    }

    public record StoredFileResponse(UUID id, UUID organizationId, UUID uploadedByUserId, String fileName,
            String contentType, long size) {
        public static StoredFileResponse from(StoredFile file) {
            return new StoredFileResponse(file.id(), file.organizationId(), file.uploadedByUserId(), file.fileName(),
                    file.contentType(), file.size());
        }
    }
}
