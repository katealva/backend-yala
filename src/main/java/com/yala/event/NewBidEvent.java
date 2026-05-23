package com.yala.event;

/**
 * Published when a bid is successfully placed on an auction.
 * Consumed by listeners that notify the previous highest bidder and broadcast
 * the updated price to the auction's WebSocket topic.
 */
public record NewBidEvent(
        Long auctionId,
        Float newBidAmount,
        Long previousBidderId,
        Long currentBidderId) {
}
