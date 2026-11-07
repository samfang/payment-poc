package com.example.paymentpoc.payment.application;

import java.util.UUID;

public record CreatePaymentCommand(UUID ownerId, String amount, String currency) {}
