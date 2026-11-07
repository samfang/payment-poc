package com.example.paymentpoc.payment.application;

import com.example.paymentpoc.payment.domain.*;
import com.example.paymentpoc.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies admin bypass: when isAdmin=true, owner check is skipped.
 * These tests fail until PaymentService methods accept an isAdmin parameter.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceAdminTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final Money MONEY = new Money(new BigDecimal("10.00"), "USD");

    @Test
    void get_as_admin_returns_payment_regardless_of_owner() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        // Admin ID is different from OWNER_ID — should still succeed
        PaymentResult result = paymentService.get(payment.id(), ADMIN_ID, true);

        assertThat(result.id()).isEqualTo(payment.id());
        assertThat(result.ownerId()).isEqualTo(OWNER_ID);
    }

    @Test
    void get_as_non_admin_different_owner_throws_not_found() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.get(payment.id(), ADMIN_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_as_admin_calls_findAll_not_findByOwner() {
        List<Payment> payments = List.of(
                Payment.create(OWNER_ID, MONEY),
                Payment.create(UUID.randomUUID(), MONEY));
        when(paymentRepository.findAll(isNull(), isNull(), eq(21))).thenReturn(payments);

        PaymentListResult result = paymentService.list(ADMIN_ID, true, null, 20);

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
        verify(paymentRepository, never()).findByOwner(any(), any(), any(), anyInt());
    }

    @Test
    void list_as_non_admin_calls_findByOwner_with_caller_id() {
        when(paymentRepository.findByOwner(eq(OWNER_ID), isNull(), isNull(), eq(21)))
                .thenReturn(List.of(Payment.create(OWNER_ID, MONEY)));

        PaymentListResult result = paymentService.list(OWNER_ID, false, null, 20);

        assertThat(result.items()).hasSize(1);
        verify(paymentRepository, never()).findAll(any(), any(), anyInt());
    }

    @Test
    void cancel_as_admin_cancels_another_owners_payment() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.cancel(payment.id(), ADMIN_ID, true);

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_as_non_admin_different_owner_throws_not_found() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancel(payment.id(), ADMIN_ID, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
