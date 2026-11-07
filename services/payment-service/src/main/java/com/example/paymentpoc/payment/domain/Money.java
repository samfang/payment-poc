package com.example.paymentpoc.payment.domain;

import java.util.Currency;

/** Immutable monetary value. Amount is in minor units (e.g. 1000 = $10.00 USD). */
public record Money(long minorUnits, String currencyCode) {

    static final long MAX_MINOR_UNITS = 99_999_999L;

    public Money {
        if (currencyCode == null || currencyCode.isBlank() || currencyCode.length() != 3) {
            throw new IllegalArgumentException(
                    "currencyCode must be a 3-letter ISO 4217 code, got: " + currencyCode);
        }
        validateCurrencyExponent(currencyCode);
        if (minorUnits <= 0) {
            throw new IllegalArgumentException("Money amount must be positive, got: " + minorUnits);
        }
        if (minorUnits > MAX_MINOR_UNITS) {
            throw new IllegalArgumentException(
                    "Money amount exceeds maximum of " + MAX_MINOR_UNITS + ", got: " + minorUnits);
        }
    }

    public int currencyExponent() {
        return Currency.getInstance(currencyCode).getDefaultFractionDigits();
    }

    private static void validateCurrencyExponent(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            if (currency.getDefaultFractionDigits() == -1) {
                throw new InvalidCurrencyException(code);
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidCurrencyException(code);
        }
    }
}
