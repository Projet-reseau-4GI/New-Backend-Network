package yowyob.comops.api.file.application.port.out;

import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FileBinaryStorage {
    Mono<String> write(String relativePath, Flux<DataBuffer> content);
    Mono<Resource> read(String relativePath);
}
