package com.yala.service;

import com.yala.dto.live.RequestFlashAuctionDTO;
import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.event.LiveAuctionCloseReason;
import com.yala.event.LiveAuctionClosedEvent;
import com.yala.event.LiveAuctionStartedEvent;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.LiveAuction;
import com.yala.model.LiveAuctionStatus;
import com.yala.model.LiveBid;
import com.yala.model.LiveStatus;
import com.yala.model.LiveStream;
import com.yala.repository.LiveAuctionRepository;
import com.yala.repository.LiveBidRepository;
import com.yala.repository.LiveStreamRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flash-auction lifecycle inside a live stream. The seller opens an auction (title, base
 * price and bid increment — default 1 sol) and later closes it: with bids it becomes SOLD
 * (a winning order is materialized by {@link com.yala.event.LiveEventListeners}); without
 * bids it becomes DESERTED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAuctionService {

    private static final float DEFAULT_BID_INCREMENT = 1.0f;

    private final LiveAuctionRepository liveAuctionRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final LiveBidRepository liveBidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LiveMapper liveMapper;

    @Transactional
    public ResponseLiveAuctionDTO create(Long streamId, RequestFlashAuctionDTO request, String sellerEmail) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Live stream not found with id: " + streamId));
        if (!stream.getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can create a flash auction in this live");
        }
        if (stream.getStatus() != LiveStatus.LIVE) {
            throw new AuctionNotActiveException("Live stream " + streamId + " is not live");
        }
        liveAuctionRepository
                .findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(streamId, LiveAuctionStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new DuplicateResourceException(
                            "There is already an active flash auction in this live; close it first");
                });

        float increment = request.bidIncrement() != null && request.bidIncrement() > 0
                ? request.bidIncrement()
                : DEFAULT_BID_INCREMENT;

        LiveAuction saved = liveAuctionRepository.save(LiveAuction.builder()
                .liveStream(stream)
                .title(request.title())
                .basePrice(request.basePrice())
                .bidIncrement(increment)
                .currentPrice(null)
                .status(LiveAuctionStatus.ACTIVE)
                .build());

        eventPublisher.publishEvent(new LiveAuctionStartedEvent(saved.getId()));
        log.info("Flash auction {} started in live {} (base {} step {})",
                saved.getId(), streamId, request.basePrice(), increment);
        return liveMapper.toAuctionDto(saved, 0);
    }

    @Transactional
    public ResponseLiveAuctionDTO close(Long auctionId, String sellerEmail) {
        LiveAuction auction = findOrThrow(auctionId);
        if (!auction.getLiveStream().getSeller().getEmail().equals(sellerEmail)) {
            throw new UnauthorizedException("Only the host can close this flash auction");
        }
        if (auction.getStatus() != LiveAuctionStatus.ACTIVE) {
            throw new AuctionNotActiveException(
                    "Flash auction " + auctionId + " is not active");
        }

        LiveBid winning = liveBidRepository
                .findFirstByLiveAuctionIdOrderByAmountDesc(auctionId)
                .orElse(null);

        auction.setEndedAt(LocalDateTime.now());
        if (winning != null) {
            auction.setStatus(LiveAuctionStatus.SOLD);
            auction.setWinner(winning.getBidder());
            auction.setWinningAmount(winning.getAmount());
        } else {
            auction.setStatus(LiveAuctionStatus.DESERTED);
        }
        liveAuctionRepository.save(auction);

        eventPublisher.publishEvent(
                new LiveAuctionClosedEvent(auction.getId(), LiveAuctionCloseReason.MANUAL));
        log.info("Flash auction {} closed as {} (winner: {})", auction.getId(), auction.getStatus(),
                auction.getWinner() != null ? auction.getWinner().getEmail() : "none");
        return liveMapper.toAuctionDto(auction, liveBidRepository.countByLiveAuctionId(auctionId));
    }

    private LiveAuction findOrThrow(Long id) {
        return liveAuctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flash auction not found with id: " + id));
    }
}
