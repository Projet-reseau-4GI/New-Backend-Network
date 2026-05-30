package com.projects.exception;

import com.projects.adapter.in.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(org.springframework.web.reactive.resource.NoResourceFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleNoResourceFoundException(
                        org.springframework.web.reactive.resource.NoResourceFoundException ex,
                        ServerWebExchange exchange) {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.builder()
                                                .timestamp(LocalDateTime.now())
                                                .status(HttpStatus.NOT_FOUND.value())
                                                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                                                .message("Route or resource not found")
                                                .path(exchange.getRequest().getPath().value())
                                                .build()));
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(ResourceNotFoundException ex,
                        ServerWebExchange exchange) {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.builder()
                                                .timestamp(LocalDateTime.now())
                                                .status(HttpStatus.NOT_FOUND.value())
                                                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                                                .message(ex.getMessage())
                                                .path(exchange.getRequest().getPath().value())
                                                .build()));
        }

        @ExceptionHandler(EmailSendException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleEmailSendException(EmailSendException ex,
                        ServerWebExchange exchange) {
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.builder()
                                                .timestamp(LocalDateTime.now())
                                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                                .error("Email Service Error")
                                                .message(ex.getMessage())
                                                .path(exchange.getRequest().getPath().value())
                                                .build()));
        }

        @ExceptionHandler(RuntimeException.class)
        public Mono<ResponseEntity<ErrorResponse>> handleRuntimeException(RuntimeException ex,
                        ServerWebExchange exchange) {
                log.error("Unhandled runtime exception at {}: {}", exchange.getRequest().getPath(), ex.getMessage(),
                                ex);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.builder()
                                                .timestamp(LocalDateTime.now())
                                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                                                .message(ex.getMessage() != null ? ex.getMessage()
                                                                : "Unknown internal server error")
                                                .path(exchange.getRequest().getPath().value())
                                                .build()));
        }

        @ExceptionHandler(Exception.class)
        public Mono<ResponseEntity<ErrorResponse>> handleGeneralException(Exception ex, ServerWebExchange exchange) {
                log.error("Unexpected error at {}: {}", exchange.getRequest().getPath(), ex.getMessage(), ex);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.builder()
                                                .timestamp(LocalDateTime.now())
                                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                                .error("Unexpected Error")
                                                .message(ex.getMessage() != null ? ex.getMessage()
                                                                : "An unexpected error occurred")
                                                .path(exchange.getRequest().getPath().value())
                                                .build()));
        }
}
