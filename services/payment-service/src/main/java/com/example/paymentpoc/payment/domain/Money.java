package com.example.paymentpoc.payment.domain;

import java.math.BigDecimal;

/** Immutable monetary value. Amount must be positive; currencyCode must be a 3-letter ISO 4217 code. */
public record Money(BigDecimal amount, String currencyCode) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Money amount must be positive, got: " + amount);
        }
        if (currencyCode == null || currencyCode.isBlank() || currencyCode.length() != 3) {
            throw new IllegalArgumentException("currencyCode must be a 3-letter ISO 4217 code, got: " + currencyCode);
        }
    }
}
