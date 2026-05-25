package com.yala.dto.bid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequestBidDTO(
        @NotNull Long auctionId,
        @NotNull @Min(0) Float amount) {
}
