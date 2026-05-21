package com.yala.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
        @NotNull Long listingId,
        @NotNull @Min(0) Float startingPrice,
        @NotNull LocalDateTime endsAt) {
}
