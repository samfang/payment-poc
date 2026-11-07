package com.example.paymentpoc.shared.exception;

import com.example.paymentpoc.payment.domain.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {

    private final PaymentStatus currentStatus;

    public InvalidPaymentStateException(PaymentStatus currentStatus) {
        super("Cannot transition payment from status: " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public PaymentStatus getCurrentStatus() {
        return currentStatus;
    }
}
