package com.example.paymentpoc.payment.infrastructure;

import com.example.paymentpoc.payment.domain.Payment;
import com.example.paymentpoc.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpa;

    @Override
    public Payment save(Payment payment) {
        return jpa.save(PaymentEntity.from(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(PaymentEntity::toDomain);
    }

    @Override
    public List<Payment> findByOwner(UUID ownerId, Instant afterCreatedAt, UUID afterId, int limit) {
        if (afterCreatedAt == null) {
            return jpa.findFirstPage(ownerId, limit).stream()
                    .map(PaymentEntity::toDomain).toList();
        }
        return jpa.findNextPage(ownerId, afterCreatedAt, afterId.toString(), limit).stream()
                .map(PaymentEntity::toDomain).toList();
    }
}
