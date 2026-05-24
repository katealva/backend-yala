package com.yala.auction.dto;

import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        Float startingPrice,
        Float currentPrice,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        String status,
        UserResponse winner,
        int totalBids) {
}
