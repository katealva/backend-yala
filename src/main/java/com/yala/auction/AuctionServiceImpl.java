package com.yala.auction;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
import com.yala.bid.Bid;
import com.yala.bid.BidRepository;
import com.yala.bid.dto.BidResponse;
import com.yala.bid.dto.CreateBidRequest;
import com.yala.exception.AuctionNotActiveException;
import com.yala.exception.DuplicateResourceException;
import com.yala.exception.InvalidBidException;
import com.yala.exception.ResourceNotFoundException;
import com.yala.exception.UnauthorizedException;
import com.yala.listing.Listing;
import com.yala.listing.ListingRepository;
import com.yala.user.User;
import com.yala.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AuctionResponse create(CreateAuctionRequest request, String sellerEmail) {
        Listing listing = listingRepository.findById(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing not found with id: " + request.listingId()));

        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedException("Only the seller of this listing can create an auction");
        }

        if (auctionRepository.findByListingId(listing.getId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Listing " + listing.getId() + " already has an auction");
        }

        if (request.endsAt().isBefore(LocalDateTime.now())) {
            throw new InvalidBidException("Auction end date must be in the future");
        }

        Auction saved = auctionRepository.save(Auction.builder()
                .listing(listing)
                .startingPrice(request.startingPrice())
                .currentPrice(request.startingPrice())
                .endsAt(request.endsAt())
                .status(AuctionStatus.ACTIVE)
                .build());

        return AuctionResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuctionResponse findById(Long id) {
        return AuctionResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuctionSummaryResponse> findAllActive(Pageable pageable) {
        return auctionRepository.findByStatus(AuctionStatus.ACTIVE, pageable)
                .map(AuctionSummaryResponse::from);
    }

    @Override
    @Transactional
    public BidResponse placeBid(CreateBidRequest request, String bidderEmail) {
        Auction auction = findOrThrow(request.auctionId());

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

        Bid bid = bidRepository.save(Bid.builder()
                .amount(request.amount())
                .auction(auction)
                .bidder(bidder)
                .build());

        // Triggers optimistic lock check — throws ObjectOptimisticLockingFailureException on conflict
        auction.setCurrentPrice(request.amount());
        auctionRepository.save(auction);

        return BidResponse.from(bid);
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeExpiredAuctions() {
        List<Auction> expired = auctionRepository
                .findByStatusAndEndsAtBefore(AuctionStatus.ACTIVE, LocalDateTime.now());

        for (Auction auction : expired) {
            bidRepository.findFirstByAuctionIdOrderByAmountDesc(auction.getId())
                    .ifPresent(winning -> auction.setWinner(winning.getBidder()));
            auction.setStatus(AuctionStatus.FINISHED);
            auctionRepository.save(auction);
            log.info("Auction {} closed. Winner: {}", auction.getId(),
                    auction.getWinner() != null ? auction.getWinner().getEmail() : "none");
        }
    }

    private Auction findOrThrow(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auction not found with id: " + id));
    }
}
