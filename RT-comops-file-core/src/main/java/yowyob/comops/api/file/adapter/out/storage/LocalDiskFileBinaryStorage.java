package yowyob.comops.api.file.adapter.out.storage;

import yowyob.comops.api.file.application.port.out.FileBinaryStorage;
import yowyob.comops.api.file.config.FileStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Profile({"r2dbc", "test-memory"})
public class LocalDiskFileBinaryStorage implements FileBinaryStorage {

    private final Path rootPath;

    public LocalDiskFileBinaryStorage(FileStorageProperties properties) {
        this.rootPath = Path.of(properties.getRootPath()).toAbsolutePath().normalize();
    }

    @Override
    public Mono<String> write(String relativePath, Flux<DataBuffer> content) {
        Path targetPath = rootPath.resolve(relativePath).normalize();
        if (!targetPath.startsWith(rootPath)) {
            return Mono.error(new IllegalArgumentException("File storage path escapes configured root."));
        }
        return Mono.fromCallable(() -> {
                    Files.createDirectories(targetPath.getParent());
                    return targetPath;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> DataBufferUtils.write(content, path)
                        .then(Mono.just(rootPath.relativize(path).toString())));
    }

    @Override
    public Mono<Resource> read(String relativePath) {
        Path targetPath = rootPath.resolve(relativePath).normalize();
        if (!targetPath.startsWith(rootPath)) {
            return Mono.error(new IOException("Stored file path escapes configured root."));
        }
        return Mono.fromCallable(() -> {
                    if (!Files.exists(targetPath)) {
                        throw new IOException("Stored file binary not found: " + relativePath);
                    }
                    return (Resource) new FileSystemResource(targetPath);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
