package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.application.PaymentResult;
import com.example.paymentpoc.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID ownerId,
        String amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<Link> links) {

    public record Link(String rel, String href, String method) {}

    public static PaymentResponse from(PaymentResult result) {
        String base = "/v1/payments/" + result.id();
        List<Link> links = new java.util.ArrayList<>();
        links.add(new Link("self", base, "GET"));
        if (result.status() == PaymentStatus.CREATED) {
            links.add(new Link("cancel", base + "/cancel", "POST"));
        }
        return new PaymentResponse(
                result.id(), result.ownerId(), result.amount(), result.currency(),
                result.status(), result.createdAt(), result.updatedAt(),
                links);
    }
}
