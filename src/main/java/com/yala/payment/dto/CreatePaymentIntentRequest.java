package com.yala.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentIntentRequest(
        @NotNull Long orderId) {
}
