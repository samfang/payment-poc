package com.example.paymentpoc.payment.application;

import com.example.paymentpoc.payment.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-level result object returned by PaymentService. The api layer maps this to a response DTO. */
public record PaymentResult(
        UUID id,
        UUID ownerId,
        String amount,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.id(),
                payment.ownerId(),
                normalizeAmount(payment.money().amount()),
                payment.money().currencyCode(),
                payment.status().name(),
                payment.createdAt(),
                payment.updatedAt());
    }

    private static String normalizeAmount(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        // Keep at least 2 decimal places (e.g. "5" → "5.00", "5.5" → "5.50")
        return stripped.scale() < 2
                ? stripped.setScale(2).toPlainString()
                : stripped.toPlainString();
    }
}
