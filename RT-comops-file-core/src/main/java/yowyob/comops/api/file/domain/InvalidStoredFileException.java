package yowyob.comops.api.file.domain;

public class InvalidStoredFileException extends RuntimeException {
    public InvalidStoredFileException(String message) {
        super(message);
    }
}