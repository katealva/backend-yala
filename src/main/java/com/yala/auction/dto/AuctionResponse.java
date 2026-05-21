package com.yala.auction.dto;

import com.yala.auction.Auction;
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

    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.getStartedAt(),
                auction.getEndsAt(),
                auction.getStatus() != null ? auction.getStatus().name() : null,
                auction.getWinner() != null ? UserResponse.from(auction.getWinner()) : null,
                auction.getBids() != null ? auction.getBids().size() : 0);
    }
}
