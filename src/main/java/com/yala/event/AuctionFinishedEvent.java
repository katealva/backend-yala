package com.yala.event;

/**
 * Published by the auction scheduler when an auction's {@code endsAt} has passed.
 * Consumed by listeners that mark the auction as FINISHED, assign the winner,
 * auto-create the order, and notify both parties.
 */
public record AuctionFinishedEvent(Long auctionId) {
}
