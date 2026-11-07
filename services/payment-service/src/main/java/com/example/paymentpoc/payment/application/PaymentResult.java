package com.example.paymentpoc.payment.application;

import com.example.paymentpoc.payment.domain.Payment;
import com.example.paymentpoc.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** Application-level result object returned by PaymentService. The api layer maps this to a response DTO. */
public record PaymentResult(
        UUID id,
        UUID ownerId,
        String amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.id(),
                payment.ownerId(),
                payment.money().amount().toPlainString(),
                payment.money().currencyCode(),
                payment.status(),
                payment.createdAt(),
                payment.updatedAt());
    }
}
