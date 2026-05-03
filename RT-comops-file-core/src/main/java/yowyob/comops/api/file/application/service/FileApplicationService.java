package yowyob.comops.api.file.application.service;

import yowyob.comops.api.file.application.port.in.GetStoredFileUseCase;
import yowyob.comops.api.file.application.port.in.StoreFileCommand;
import yowyob.comops.api.file.application.port.in.StoreFileUseCase;
import yowyob.comops.api.file.application.port.out.FileBinaryStorage;
import yowyob.comops.api.file.application.port.out.KycVerificationPort;
import yowyob.comops.api.file.application.port.out.StoredFileRepository;
import yowyob.comops.api.file.config.FileStorageProperties;
import yowyob.comops.api.file.domain.InvalidStoredFileException;
import yowyob.comops.api.file.domain.StoredFileNotFoundException;
import yowyob.comops.api.file.domain.model.StoredFile;
import yowyob.comops.api.file.application.port.in.RecordSystemAuditUseCase;
import yowyob.comops.api.kernel.application.service.ReactiveRequestContextHolder;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class FileApplicationService implements StoreFileUseCase, GetStoredFileUseCase {

    private final StoredFileRepository storedFileRepository;
    private final FileBinaryStorage fileBinaryStorage;
    private final RecordSystemAuditUseCase recordSystemAuditUseCase;
    private final FileStorageProperties fileStorageProperties;
    private final KycVerificationPort kycVerificationPort;

    public FileApplicationService(StoredFileRepository storedFileRepository, FileBinaryStorage fileBinaryStorage,
                                  RecordSystemAuditUseCase recordSystemAuditUseCase, FileStorageProperties fileStorageProperties, KycVerificationPort kycVerificationPort) {
        this.storedFileRepository = storedFileRepository;
        this.fileBinaryStorage = fileBinaryStorage;
        this.recordSystemAuditUseCase = recordSystemAuditUseCase;
        this.fileStorageProperties = fileStorageProperties;
        this.kycVerificationPort = kycVerificationPort;
    }

    @Override
    public Mono<StoredFile> store(StoreFileCommand command) {
        String normalizedFileName = normalizeFileName(command.fileName());
        validate(command, normalizedFileName);

        return ReactiveRequestContextHolder.getRequiredContext()
                .flatMap(context ->
                        kycVerificationPort.verify(context.tenantId(), command)
                                .then(Mono.defer(() -> {
                                    String relativePath = context.tenantId() + "/" + UUID.randomUUID() + "/" + normalizedFileName;
                                    return fileBinaryStorage.write(relativePath, command.content())
                                            .flatMap(storedPath -> storedFileRepository.save(
                                                            StoredFile.create(context.tenantId(), context.organizationId(),
                                                                    context.userId(), normalizedFileName, command.contentType(),
                                                                    command.size(), storedPath))
                                                    .flatMap(saved -> recordSystemAuditUseCase.record(
                                                                    saved.tenantId(), saved.organizationId(), saved.uploadedByUserId(),
                                                                    "FILE_UPLOADED", "STORED_FILE", saved.id().toString(), saved.fileName())
                                                            .thenReturn(saved)));
                                }))
                );
    }

    @Override
    public Mono<StoredFile> getMetadata(UUID fileId) {
        return ReactiveRequestContextHolder.getRequiredContext()
                .flatMap(context -> storedFileRepository.findById(context.tenantId(), fileId))
                .switchIfEmpty(Mono.error(new StoredFileNotFoundException(fileId)));
    }

    @Override
    public Mono<Resource> loadResource(UUID fileId) {
        return getMetadata(fileId)
                .flatMap(metadata -> fileBinaryStorage.read(metadata.storagePath()));
    }

    private void validate(StoreFileCommand command, String normalizedFileName) {
        if (normalizedFileName.isBlank()) {
            throw new InvalidStoredFileException("File name is required.");
        }
        if (command.contentType() == null || command.contentType().isBlank()) {
            throw new InvalidStoredFileException("Content type is required.");
        }
        if (command.size() > 0 && command.size() > fileStorageProperties.getMaxFileSizeBytes()) {
            throw new InvalidStoredFileException(
                    "File size exceeds configured limit: " + fileStorageProperties.getMaxFileSizeBytes());
        }
        if (!fileStorageProperties.getAllowedContentTypes().isEmpty()) {
            String contentType = command.contentType().toLowerCase(Locale.ROOT);
            boolean allowed = fileStorageProperties.getAllowedContentTypes().stream()
                    .map(type -> type.toLowerCase(Locale.ROOT))
                    .anyMatch(contentType::equals);
            if (!allowed) {
                throw new InvalidStoredFileException("Content type is not allowed: " + command.contentType());
            }
        }
    }

    private String normalizeFileName(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return "";
        }
        String fileName = Path.of(rawFileName).getFileName().toString().trim();
        return fileName.replaceAll("[\\r\\n\\t]", "_");
    }
}
