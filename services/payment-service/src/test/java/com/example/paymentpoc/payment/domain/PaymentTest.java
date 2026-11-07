package com.example.paymentpoc.payment.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PaymentTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final Money MONEY = new Money(new BigDecimal("49.99"), "USD");

    @Test
    void should_create_payment_with_status_CREATED() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(payment.id()).isNotNull();
        assertThat(payment.ownerId()).isEqualTo(OWNER_ID);
        assertThat(payment.money()).isEqualTo(MONEY);
        assertThat(payment.createdAt()).isNotNull();
        assertThat(payment.updatedAt()).isNotNull();
    }

    @Test
    void should_allow_CREATED_to_CANCELLED_transition() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        payment.cancel();
        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void should_update_updatedAt_when_cancelled() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        var before = payment.updatedAt();
        payment.cancel();
        assertThat(payment.updatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void should_reject_cancelling_a_COMPLETED_payment() {
        Payment payment = Payment.reconstitute(UUID.randomUUID(), OWNER_ID, MONEY, PaymentStatus.COMPLETED,
                java.time.Instant.now(), java.time.Instant.now());
        assertThatThrownBy(payment::cancel)
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void should_reject_cancelling_a_CANCELLED_payment() {
        Payment payment = Payment.reconstitute(UUID.randomUUID(), OWNER_ID, MONEY, PaymentStatus.CANCELLED,
                java.time.Instant.now(), java.time.Instant.now());
        assertThatThrownBy(payment::cancel)
                .isInstanceOf(InvalidPaymentStateException.class);
    }
}
