package com.example.paymentpoc.payment.domain;

import com.example.paymentpoc.shared.exception.DomainException;

public class InvalidCurrencyException extends DomainException {

    public InvalidCurrencyException(String currencyCode) {
        super("VALIDATION_ERROR", 400,
                "Currency '" + currencyCode + "' is not supported or has no minor unit definition.",
                "currency");
    }
}
