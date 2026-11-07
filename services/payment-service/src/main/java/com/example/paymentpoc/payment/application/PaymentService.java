package com.example.paymentpoc.payment.application;

import java.util.UUID;

public interface PaymentService {

    PaymentResult create(CreatePaymentCommand command);

    /** Returns the payment. If {@code isAdmin} is true, skips the owner check. */
    PaymentResult get(UUID id, UUID callerId, boolean isAdmin);

    /** Lists payments. If {@code isAdmin} is true, returns all owners' payments unfiltered. */
    PaymentListResult list(UUID callerId, boolean isAdmin, String cursor, int pageSize);

    /** Cancels the payment. If {@code isAdmin} is true, skips the owner check. */
    PaymentResult cancel(UUID id, UUID callerId, boolean isAdmin);
}
