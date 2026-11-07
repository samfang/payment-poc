package com.example.paymentpoc.payment.domain;

public enum PaymentStatus {
    CREATED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case CREATED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
