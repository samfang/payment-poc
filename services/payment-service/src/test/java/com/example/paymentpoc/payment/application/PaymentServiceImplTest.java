package com.example.paymentpoc.payment.application;

import com.example.paymentpoc.payment.domain.*;
import com.example.paymentpoc.shared.exception.InvalidPaymentStateException;
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

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final Money MONEY = new Money(new BigDecimal("50.00"), "USD");

    @Test
    void create_should_persist_payment_with_CREATED_status() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.create(new CreatePaymentCommand(OWNER_ID, "50.00", "USD"));

        assertThat(result.status()).isEqualTo(PaymentStatus.CREATED);
        assertThat(result.amount()).isEqualTo("50.00");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.ownerId()).isEqualTo(OWNER_ID);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void get_should_return_result_for_known_id() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        PaymentResult result = paymentService.get(payment.id(), OWNER_ID);

        assertThat(result.id()).isEqualTo(payment.id());
    }

    @Test
    void get_should_throw_not_found_for_unknown_id() {
        UUID unknownId = UUID.randomUUID();
        when(paymentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.get(unknownId, OWNER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_should_throw_not_found_when_owner_does_not_match() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.get(payment.id(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_should_return_trimmed_page_without_next_cursor_when_no_extra_row() {
        List<Payment> payments = List.of(Payment.create(OWNER_ID, MONEY));
        when(paymentRepository.findByOwner(eq(OWNER_ID), isNull(), isNull(), eq(21)))
                .thenReturn(payments);

        PaymentListResult result = paymentService.list(OWNER_ID, null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void list_should_set_next_cursor_when_extra_row_exists() {
        // Return pageSize+1 results to signal more data
        List<Payment> payments = List.of(
                Payment.create(OWNER_ID, MONEY),
                Payment.create(OWNER_ID, MONEY));
        when(paymentRepository.findByOwner(eq(OWNER_ID), isNull(), isNull(), eq(2)))
                .thenReturn(payments);

        PaymentListResult result = paymentService.list(OWNER_ID, null, 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    void cancel_CREATED_payment_should_return_CANCELLED_result() {
        Payment payment = Payment.create(OWNER_ID, MONEY);
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResult result = paymentService.cancel(payment.id(), OWNER_ID);

        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void cancel_COMPLETED_payment_should_throw_InvalidPaymentStateException() {
        Payment payment = Payment.reconstitute(UUID.randomUUID(), OWNER_ID, MONEY,
                PaymentStatus.COMPLETED, Instant.now(), Instant.now());
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancel(payment.id(), OWNER_ID))
                .isInstanceOf(InvalidPaymentStateException.class);
    }

    @Test
    void cancel_CANCELLED_payment_should_throw_InvalidPaymentStateException() {
        Payment payment = Payment.reconstitute(UUID.randomUUID(), OWNER_ID, MONEY,
                PaymentStatus.CANCELLED, Instant.now(), Instant.now());
        when(paymentRepository.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancel(payment.id(), OWNER_ID))
                .isInstanceOf(InvalidPaymentStateException.class);
    }
}
