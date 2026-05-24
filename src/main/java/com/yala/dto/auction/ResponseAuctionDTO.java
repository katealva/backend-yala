package com.yala.dto.auction;

import com.yala.dto.user.ResponseUserDTO;
import java.time.LocalDateTime;

public record ResponseAuctionDTO(
        Long id,
        Float startingPrice,
        Float currentPrice,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        String status,
        ResponseUserDTO winner,
        int totalBids) {
}
