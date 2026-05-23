package com.yala.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by the auction scheduler when an auction transitions to FINISHED.
 * Consumers create the order for the winner and notify both parties.
 */
@Getter
public class AuctionFinishedEvent extends ApplicationEvent {

    private final Long auctionId;

    public AuctionFinishedEvent(Object source, Long auctionId) {
        super(source);
        this.auctionId = auctionId;
    }
}
