package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.payment.api.PaymentResponse.Link;
import com.example.paymentpoc.payment.application.PaymentListResult;
import java.util.ArrayList;
import java.util.List;

public record PaymentListResponse(List<PaymentResponse> items, String nextCursor, List<Link> links) {

    public static PaymentListResponse from(PaymentListResult result, String selfUrl) {
        List<Link> links = new ArrayList<>();
        links.add(new Link("self", selfUrl, "GET"));
        if (result.nextCursor() != null) {
            String nextUrl = selfUrl.contains("?")
                    ? selfUrl + "&cursor=" + result.nextCursor()
                    : selfUrl + "?cursor=" + result.nextCursor();
            links.add(new Link("next", nextUrl, "GET"));
        }
        List<PaymentResponse> items = result.items().stream().map(PaymentResponse::from).toList();
        return new PaymentListResponse(items, result.nextCursor(), links);
    }
}
