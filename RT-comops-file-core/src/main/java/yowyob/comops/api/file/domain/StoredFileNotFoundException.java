package yowyob.comops.api.file.domain;

import java.util.UUID;

public class StoredFileNotFoundException extends RuntimeException {
    public StoredFileNotFoundException(UUID fileId) {
        super("File not found: " + fileId);
    }
}