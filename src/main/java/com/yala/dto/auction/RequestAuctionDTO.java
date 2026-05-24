package com.yala.dto.auction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RequestAuctionDTO(
        @NotNull Long listingId,
        @NotNull @Min(0) Float startingPrice,
        @NotNull LocalDateTime endsAt) {
}
