package com.yala.auction.dto;

import com.yala.auction.Auction;
import java.time.LocalDateTime;

public record AuctionSummaryResponse(
        Long id,
        Float currentPrice,
        LocalDateTime endsAt,
        String status) {

    public static AuctionSummaryResponse from(Auction auction) {
        return new AuctionSummaryResponse(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getEndsAt(),
                auction.getStatus() != null ? auction.getStatus().name() : null);
    }
}
