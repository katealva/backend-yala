package com.yala.auction;
import com.yala.model.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredAuctions() {
        log.debug("AuctionScheduler: checking for expired auctions");
        auctionService.closeExpiredAuctions();
    }
}
