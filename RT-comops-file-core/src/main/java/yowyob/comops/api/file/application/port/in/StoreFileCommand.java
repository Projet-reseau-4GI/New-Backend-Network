package yowyob.comops.api.file.application.port.in;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

public record StoreFileCommand(
        String fileName,
        String contentType,
        long size,
        Flux<DataBuffer> content) {
}
