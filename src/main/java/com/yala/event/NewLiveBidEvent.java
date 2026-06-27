package com.yala.event;

/**
 * Published when a bid is placed on a flash auction. Consumed by listeners that notify the
 * outbid bidder and broadcast the updated price to the live's WebSocket topic.
 */
public record NewLiveBidEvent(
        Long liveAuctionId,
        Float newBidAmount,
        Long previousBidderId,
        Long currentBidderId) {
}
