package com.example.paymentpoc.payment.infrastructure;

import com.example.paymentpoc.payment.domain.Money;
import com.example.paymentpoc.payment.domain.Payment;
import com.example.paymentpoc.payment.domain.PaymentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    static PaymentEntity from(Payment domain) {
        PaymentEntity entity = new PaymentEntity();
        entity.id = domain.id();
        entity.ownerId = domain.ownerId();
        entity.amount = domain.money().minorUnits();
        entity.currencyCode = domain.money().currencyCode();
        entity.status = domain.status();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    Payment toDomain() {
        return Payment.reconstitute(id, ownerId, new Money(amount, currencyCode),
                status, createdAt, updatedAt);
    }

    UUID getId() { return id; }
    Instant getCreatedAt() { return createdAt; }
}
