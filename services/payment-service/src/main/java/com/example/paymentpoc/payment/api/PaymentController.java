package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.*;
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

        UUID ownerId = UUID.fromString(jwt.getSubject());
        String bodyHash = request.amount() + "|" + request.currency();

        @SuppressWarnings("unchecked")
        var cached = (java.util.Optional<ResponseEntity<PaymentResponse>>) (java.util.Optional<?>)
                idempotencyStore.getOrAcquire(ownerId.toString(), idempotencyKey, bodyHash);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            PaymentResult result = paymentService.create(
                    new CreatePaymentCommand(ownerId, request.amount(), request.currency()));
            PaymentResponse body = PaymentResponse.from(result);
            ResponseEntity<PaymentResponse> response = ResponseEntity
                    .created(URI.create("/v1/payments/" + result.id()))
                    .body(body);
            idempotencyStore.store(ownerId.toString(), idempotencyKey, bodyHash, response);
            return response;
        } catch (Exception e) {
            idempotencyStore.release(ownerId.toString(), idempotencyKey);
            throw e;
        }
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
            @AuthenticationPrincipal Jwt jwt) {

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE + ", got: " + pageSize);
        }

        UUID ownerId = UUID.fromString(jwt.getSubject());
        PaymentListResult result = paymentService.list(ownerId, cursor, pageSize);

        // Build self URL as an absolute path (no host), always including pageSize
        String selfUrl = "/v1/payments?pageSize=" + pageSize;
        if (cursor != null) selfUrl += "&cursor=" + cursor;

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
