package com.example.paymentpoc.payment.application;

import java.util.UUID;

public interface PaymentService {

    PaymentResult create(CreatePaymentCommand command);

    PaymentResult get(UUID id, UUID ownerId);

    PaymentListResult list(UUID ownerId, String cursor, int pageSize);

    PaymentResult cancel(UUID id, UUID ownerId);
}
