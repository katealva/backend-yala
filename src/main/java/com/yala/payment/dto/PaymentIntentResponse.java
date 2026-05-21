package com.yala.payment.dto;

public record PaymentIntentResponse(
        String clientSecret,
        String paymentIntentId) {
}
