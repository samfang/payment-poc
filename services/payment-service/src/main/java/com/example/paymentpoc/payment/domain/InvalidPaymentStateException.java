package com.example.paymentpoc.payment.domain;

import com.example.paymentpoc.shared.exception.DomainException;

public class InvalidPaymentStateException extends DomainException {

    public InvalidPaymentStateException(PaymentStatus currentStatus) {
        super("INVALID_PAYMENT_STATE", 409, "Cannot transition payment from status: " + currentStatus);
    }
}
