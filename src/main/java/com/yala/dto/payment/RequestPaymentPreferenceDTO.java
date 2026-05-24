package com.yala.dto.payment;

import jakarta.validation.constraints.NotNull;

public record RequestPaymentPreferenceDTO(
        @NotNull Long orderId) {
}
