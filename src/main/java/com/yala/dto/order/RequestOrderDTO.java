package com.yala.dto.order;

import jakarta.validation.constraints.NotNull;

public record RequestOrderDTO(
        @NotNull Long listingId) {
}
