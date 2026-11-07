package com.example.paymentpoc.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** Payment domain model. No Spring or JPA annotations. */
public class Payment {

    private final UUID id;
    private final UUID ownerId;
    private final Money money;
    private PaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Payment(UUID id, UUID ownerId, Money money, PaymentStatus status,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.money = money;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factory: creates a new payment in CREATED state. */
    public static Payment create(UUID ownerId, Money money) {
        Instant now = Instant.now();
        return new Payment(UUID.randomUUID(), ownerId, money, PaymentStatus.CREATED, now, now);
    }

    /** Factory: reconstitutes a payment from persistent storage. */
    public static Payment reconstitute(UUID id, UUID ownerId, Money money, PaymentStatus status,
                                       Instant createdAt, Instant updatedAt) {
        return new Payment(id, ownerId, money, status, createdAt, updatedAt);
    }

    /** Transitions this payment to CANCELLED. Throws if the transition is not allowed. */
    public void cancel() {
        if (!status.canTransitionTo(PaymentStatus.CANCELLED)) {
            throw new InvalidPaymentStateException(status);
        }
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Money money() { return money; }
    public PaymentStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
