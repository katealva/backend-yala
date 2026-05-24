package com.yala.dto.auction;

import java.time.LocalDateTime;

public record ResponseAuctionSummaryDTO(
        Long id,
        Float currentPrice,
        LocalDateTime endsAt,
        String status) {
}
