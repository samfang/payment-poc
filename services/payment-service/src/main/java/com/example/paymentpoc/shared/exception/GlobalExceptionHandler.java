package com.example.paymentpoc.shared.exception;

import com.example.paymentpoc.shared.config.RequestIdFilter;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(DomainException ex) {
        String debugId = newDebugId();
        log.warn("Domain rule violation [debugId={}, code={}]: {}", debugId, ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatusCode())
                .header("X-Error-Code", ex.getErrorCode())
                .body(ApiError.of(ex.getErrorCode(), ex.getMessage(), debugId));
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateIdempotency(DuplicateIdempotencyKeyException ex) {
        String debugId = newDebugId();
        log.warn("Duplicate idempotency key [debugId={}] key={}", debugId, ex.getIdempotencyKey());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("X-Error-Code", "DUPLICATE_IDEMPOTENCY_KEY")
                .body(ApiError.of("DUPLICATE_IDEMPOTENCY_KEY", ex.getMessage(), debugId));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        String debugId = newDebugId();
        log.warn("Resource not found [debugId={}]: {}", debugId, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .header("X-Error-Code", "RESOURCE_NOT_FOUND")
                .body(ApiError.of("RESOURCE_NOT_FOUND", ex.getMessage(), debugId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String debugId = newDebugId();
        List<ApiError.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toDetail)
                .toList();
        ApiError body = new ApiError(new ApiError.ErrorBody(
                "VALIDATION_ERROR",
                "One or more request fields failed validation.",
                null,
                details,
                debugId));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        String debugId = newDebugId();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(ApiError.of("VALIDATION_ERROR",
                        "Required request header '" + ex.getHeaderName() + "' is missing.", debugId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadBody(HttpMessageNotReadableException ex) {
        String debugId = newDebugId();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(ApiError.of("VALIDATION_ERROR", "Request body is missing or malformed.", debugId));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String debugId = newDebugId();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(ApiError.of("VALIDATION_ERROR",
                        "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.",
                        ex.getName(), debugId));
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ApiError> handleInvalidCursor(InvalidCursorException ex) {
        String debugId = newDebugId();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(ApiError.of("VALIDATION_ERROR", ex.getMessage(), "cursor", debugId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        String debugId = newDebugId();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header("X-Error-Code", "VALIDATION_ERROR")
                .body(ApiError.of("VALIDATION_ERROR", ex.getMessage(), debugId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        String debugId = newDebugId();
        log.warn("Access denied [debugId={}]", debugId);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .header("X-Error-Code", "FORBIDDEN")
                .body(ApiError.of("FORBIDDEN", "You do not have permission to perform this action.", debugId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        String debugId = newDebugId();
        log.error("Unexpected error [debugId={}]", debugId, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Error-Code", "INTERNAL_ERROR")
                .body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred.", debugId));
    }

    private ApiError.ErrorDetail toDetail(FieldError fe) {
        return new ApiError.ErrorDetail(
                "FIELD_INVALID",
                fe.getDefaultMessage(),
                fe.getField());
    }

    private String newDebugId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }
}
