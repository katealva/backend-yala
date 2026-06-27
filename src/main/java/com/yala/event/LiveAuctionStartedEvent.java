package com.yala.event;

/** Published when a seller opens a new flash auction inside a live stream. */
public record LiveAuctionStartedEvent(Long liveAuctionId) {
}
