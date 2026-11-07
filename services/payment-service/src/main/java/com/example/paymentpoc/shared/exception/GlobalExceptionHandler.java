package com.example.paymentpoc.shared.exception;

import com.example.paymentpoc.payment.domain.PaymentStatus;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidPaymentStateException ex) {
        String debugId = newDebugId();
        PaymentStatus current = ex.getCurrentStatus();
        log.warn("Invalid payment state transition [debugId={}] currentStatus={}", debugId, current);
        ApiError body = new ApiError(new ApiError.ErrorBody(
                "INVALID_PAYMENT_STATE",
                ex.getMessage(),
                null,
                List.of(new ApiError.ErrorDetail("INVALID_STATE", ex.getMessage(), "status")),
                debugId));
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("X-Error-Code", "INVALID_PAYMENT_STATE")
                .body(body);
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
        return "dbg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
