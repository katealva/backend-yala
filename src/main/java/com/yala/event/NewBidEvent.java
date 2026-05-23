package com.yala.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published asynchronously when a {@code Bid} is successfully persisted.
 * Consumers notify the previous (now-outbid) bidder if any.
 */
@Getter
public class NewBidEvent extends ApplicationEvent {

    private final Long auctionId;
    private final Float newAmount;
    private final Long previousBidderId;
    private final Long currentBidderId;

    public NewBidEvent(
            Object source,
            Long auctionId,
            Float newAmount,
            Long previousBidderId,
            Long currentBidderId) {
        super(source);
        this.auctionId = auctionId;
        this.newAmount = newAmount;
        this.previousBidderId = previousBidderId;
        this.currentBidderId = currentBidderId;
    }
}
