package com.yala.event;

/**
 * Published when a flash auction is closed (by the seller, or automatically when a bid reaches the
 * maximum allowed amount). Consumed by the listener that materializes the winning order (with a 48h
 * payment deadline) and notifies winner/seller, or broadcasts the deserted result. {@code reason}
 * lets the listener pick a distinct chat message for a max-reached close vs. a manual close.
 */
public record LiveAuctionClosedEvent(Long liveAuctionId, LiveAuctionCloseReason reason) {
}
