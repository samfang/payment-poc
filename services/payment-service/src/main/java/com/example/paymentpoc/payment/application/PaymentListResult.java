package com.example.paymentpoc.payment.application;

import java.util.List;

public record PaymentListResult(List<PaymentResult> items, String nextCursor) {}
