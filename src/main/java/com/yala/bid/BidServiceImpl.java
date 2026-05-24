package com.yala.bid;

import com.yala.auction.Auction;
import com.yala.auction.AuctionRepository;
import com.yala.auction.AuctionStatus;
import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
import com.yala.event.NewBidEvent;
import com.yala.exceptions.AuctionNotActiveException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bidding domain service. Owns the lifecycle of placing a bid and reading the
 * bid history of an auction. Extracted from {@code AuctionServiceImpl} so the
 * auction service stops carrying responsibilities outside its aggregate (SRP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BidResponse placeBid(CreateBidRequest request, String bidderEmail) {
        Auction auction = auctionRepository.findById(request.auctionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auction not found with id: " + request.auctionId()));

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionNotActiveException(
                    "Auction " + auction.getId() + " is not active");
        }
        if (auction.getEndsAt().isBefore(LocalDateTime.now())) {
            throw new AuctionNotActiveException("Auction has already ended");
        }

        User bidder = userRepository.findByEmail(bidderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (auction.getListing().getSeller().getId().equals(bidder.getId())) {
            throw new InvalidBidException("The seller cannot bid on their own auction");
        }
        if (request.amount() <= auction.getCurrentPrice()) {
            throw new InvalidBidException(
                    "Bid must be greater than current price: " + auction.getCurrentPrice());
        }

        Long previousBidderId = bidRepository
                .findFirstByAuctionIdOrderByAmountDesc(auction.getId())
                .map(Bid::getBidder)
                .map(User::getId)
                .filter(id -> !id.equals(bidder.getId()))
                .orElse(null);

        Bid bid = bidRepository.save(Bid.builder()
                .amount(request.amount())
                .auction(auction)
                .bidder(bidder)
                .build());

        // Triggers optimistic-lock check on Auction.currentPrice — throws
        // ObjectOptimisticLockingFailureException on conflict, mapped to 409
        // by GlobalExceptionsHandler.
        auction.setCurrentPrice(request.amount());
        auctionRepository.save(auction);

        eventPublisher.publishEvent(new NewBidEvent(
                auction.getId(), request.amount(), previousBidderId, bidder.getId()));

        log.info("Bid {} placed on auction {} by {} for amount {}",
                bid.getId(), auction.getId(), bidder.getEmail(), request.amount());
        return BidResponse.from(bid);
    }

    @SuppressWarnings("unused")
    private static <T> Optional<T> nullSafe(Optional<T> opt) {
        return opt;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BidResponse> findByAuction(Long auctionId, Pageable pageable) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new ResourceNotFoundException("Auction not found with id: " + auctionId);
        }
        return bidRepository.findByAuctionId(auctionId, pageable).map(BidResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public BidResponse findHighest(Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new ResourceNotFoundException("Auction not found with id: " + auctionId);
        }
        return bidRepository.findFirstByAuctionIdOrderByAmountDesc(auctionId)
                .map(BidResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bids found for auction: " + auctionId));
    }
}
