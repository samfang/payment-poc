package com.example.paymentpoc.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for the Payment aggregate. No Spring or JPA annotations. */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    /**
     * Returns up to {@code limit} payments for the given owner, ordered by (createdAt, id) ascending.
     * If {@code afterCreatedAt} and {@code afterId} are non-null, returns only payments after that position.
     * Pass nulls for the first page.
     */
    List<Payment> findByOwner(UUID ownerId, Instant afterCreatedAt, UUID afterId, int limit);

    /**
     * Returns up to {@code limit} payments across ALL owners, ordered by (createdAt, id) ascending.
     * Used for admin queries. Pass nulls for the first page.
     */
    List<Payment> findAll(Instant afterCreatedAt, UUID afterId, int limit);
}
