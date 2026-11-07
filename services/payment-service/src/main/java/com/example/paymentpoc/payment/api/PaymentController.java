package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final PaymentService paymentService;
    private final IdempotencyStore idempotencyStore;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        @SuppressWarnings("unchecked")
        var cached = (java.util.Optional<ResponseEntity<?>>) (java.util.Optional<?>) idempotencyStore.getOrAcquire(idempotencyKey);
        if (cached.isPresent()) {
            return (ResponseEntity<PaymentResponse>) cached.get();
        }

        UUID ownerId = UUID.fromString(jwt.getSubject());
        PaymentResult result = paymentService.create(new CreatePaymentCommand(ownerId, request.amount(), request.currency()));
        PaymentResponse body = PaymentResponse.from(result);

        ResponseEntity<PaymentResponse> response = ResponseEntity
                .created(URI.create("/v1/payments/" + result.id()))
                .body(body);

        idempotencyStore.store(idempotencyKey, response);
        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID ownerId = UUID.fromString(jwt.getSubject());
        PaymentResult result = paymentService.get(id, ownerId);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<PaymentListResponse> listPayments(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        int clampedSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        UUID ownerId = UUID.fromString(jwt.getSubject());
        PaymentListResult result = paymentService.list(ownerId, cursor, clampedSize);

        String selfUrl = httpRequest.getRequestURL().toString();
        return ResponseEntity.ok(PaymentListResponse.from(result, selfUrl));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        UUID ownerId = UUID.fromString(jwt.getSubject());
        PaymentResult result = paymentService.cancel(id, ownerId);
        return ResponseEntity.ok(PaymentResponse.from(result));
    }
}
