package com.yala.auction.dto;

import java.time.LocalDateTime;

public record AuctionSummaryResponse(
        Long id,
        Float currentPrice,
        LocalDateTime endsAt,
        String status) {
}
