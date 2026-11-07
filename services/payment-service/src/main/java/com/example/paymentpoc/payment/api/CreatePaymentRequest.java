package com.example.paymentpoc.payment.api;

import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @NotBlank(message = "amount is required")
        // Rejects zero ("0", "0.0", "0.0000") via negative lookahead; allows "0.01", "1", "100.5000"
        @Pattern(regexp = "^(?!0+(\\.0+)?$)\\d+(\\.\\d{1,4})?$",
                message = "amount must be a positive decimal number with up to 4 decimal places")
        String amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be uppercase letters")
        String currency) {
}
