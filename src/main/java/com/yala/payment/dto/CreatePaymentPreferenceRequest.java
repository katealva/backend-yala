package com.yala.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentPreferenceRequest(
        @NotNull Long orderId) {
}
