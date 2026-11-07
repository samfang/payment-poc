package com.example.paymentpoc.payment.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record CreatePaymentRequest(
        @JsonDeserialize(using = AmountDeserializer.class)
        @NotNull(message = "amount is required")
        @Min(value = 1, message = "amount must be a positive integer")
        @Max(value = 99_999_999, message = "amount must not exceed 99999999")
        Long amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be uppercase letters")
        String currency) {
}
