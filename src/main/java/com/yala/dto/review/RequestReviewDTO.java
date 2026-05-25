package com.yala.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestReviewDTO(
        @NotNull Long orderId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment) {
}
