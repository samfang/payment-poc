package com.example.paymentpoc.payment.api;

import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @NotBlank(message = "amount is required")
        @Pattern(regexp = "^\\d+(\\.\\d{1,4})?$", message = "amount must be a positive decimal number")
        String amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be uppercase letters")
        String currency) {
}
