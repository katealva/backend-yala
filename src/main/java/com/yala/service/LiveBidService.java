package com.yala.service;

import com.yala.dto.live.RequestLiveBidDTO;
import com.yala.dto.live.ResponseLiveBidDTO;
import com.yala.event.NewLiveBidEvent;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.model.LiveAuction;
import com.yala.model.LiveAuctionStatus;
import com.yala.model.LiveBid;
import com.yala.model.LiveStatus;
import com.yala.model.User;
import com.yala.repository.LiveAuctionRepository;
import com.yala.repository.LiveBidRepository;
import com.yala.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bidding on flash auctions. Mirrors {@link BidService}: the auction's {@code currentPrice}
 * carries a {@code @Version}, so concurrent bids surface as an optimistic-lock conflict (409).
 * The minimum next bid is {@code basePrice} for the first bid, then {@code currentPrice +
 * bidIncrement}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveBidService {

    private final LiveBidRepository liveBidRepository;
    private final LiveAuctionRepository liveAuctionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LiveMapper liveMapper;

    @Transactional
    public ResponseLiveBidDTO place(Long auctionId, RequestLiveBidDTO request, String bidderEmail) {
        LiveAuction auction = liveAuctionRepository.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flash auction not found with id: " + auctionId));

        if (auction.getStatus() != LiveAuctionStatus.ACTIVE) {
            throw new AuctionNotActiveException("Flash auction " + auctionId + " is not active");
        }
        if (auction.getLiveStream().getStatus() != LiveStatus.LIVE) {
            throw new AuctionNotActiveException("The live stream has ended");
        }

        User bidder = userRepository.findByEmail(bidderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (auction.getLiveStream().getSeller().getId().equals(bidder.getId())) {
            throw new InvalidBidException("The host cannot bid on their own flash auction");
        }

        float minNext = auction.getCurrentPrice() == null
                ? auction.getBasePrice()
                : auction.getCurrentPrice() + auction.getBidIncrement();
        if (request.amount() < minNext) {
            throw new InvalidBidException("Bid must be at least " + minNext);
        }

        Long previousBidderId = liveBidRepository
                .findFirstByLiveAuctionIdOrderByAmountDesc(auctionId)
                .map(LiveBid::getBidder)
                .map(User::getId)
                .filter(id -> !id.equals(bidder.getId()))
                .orElse(null);

        LiveBid bid = liveBidRepository.save(LiveBid.builder()
                .amount(request.amount())
                .liveAuction(auction)
                .bidder(bidder)
                .build());

        // Optimistic-lock check on LiveAuction.currentPrice (409 on conflict).
        auction.setCurrentPrice(request.amount());
        liveAuctionRepository.save(auction);

        eventPublisher.publishEvent(new NewLiveBidEvent(
                auction.getId(), request.amount(), previousBidderId, bidder.getId()));

        log.info("Live bid {} placed on flash auction {} by {} for {}",
                bid.getId(), auction.getId(), bidder.getEmail(), request.amount());
        return liveMapper.toBidDto(bid);
    }

    @Transactional(readOnly = true)
    public Page<ResponseLiveBidDTO> findByAuction(Long auctionId, Pageable pageable) {
        if (!liveAuctionRepository.existsById(auctionId)) {
            throw new ResourceNotFoundException("Flash auction not found with id: " + auctionId);
        }
        return liveBidRepository.findByLiveAuctionId(auctionId, pageable)
                .map(liveMapper::toBidDto);
    }
}
