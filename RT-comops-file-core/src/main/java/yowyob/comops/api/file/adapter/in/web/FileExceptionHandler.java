package yowyob.comops.api.file.adapter.in.web;

import yowyob.comops.api.common.domain.model.ApiResponse;
import yowyob.comops.api.file.domain.InvalidStoredFileException;
import yowyob.comops.api.file.domain.KycValidationException;
import yowyob.comops.api.file.domain.StoredFileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FileController.class)
public class FileExceptionHandler {

    @ExceptionHandler(StoredFileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoredFileNotFound(StoredFileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(exception.getMessage(), "STORED_FILE_NOT_FOUND"));
    }

    @ExceptionHandler(InvalidStoredFileException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStoredFile(InvalidStoredFileException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(exception.getMessage(), "INVALID_STORED_FILE"));
    }

    @ExceptionHandler(KycValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleKycFailure(KycValidationException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("KYC_REJECTED", ex.getReasons().toString()));
    }
}
