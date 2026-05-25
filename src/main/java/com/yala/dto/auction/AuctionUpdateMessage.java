package com.yala.dto.auction;

public record AuctionUpdateMessage(
        Long auctionId,
        Float currentPrice,
        Integer totalBids,
        String status,
        LatestBidInfo latestBid,
        String winnerUsername) {
}
