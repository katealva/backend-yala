package com.yala.event;

/**
 * Published when a flash auction is closed (by the seller or when the live ends).
 * Consumed by the listener that materializes the winning order (with a 48h payment
 * deadline) and notifies winner/seller, or broadcasts the deserted result.
 */
public record LiveAuctionClosedEvent(Long liveAuctionId) {
}
