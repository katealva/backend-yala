package com.yala.model;

/**
 * Lifecycle of a flash auction held inside a live stream:
 * ACTIVE while open for bids, SOLD when closed with at least one bid,
 * DESERTED when closed without bids.
 */
public enum LiveAuctionStatus {
    ACTIVE,
    SOLD,
    DESERTED
}
