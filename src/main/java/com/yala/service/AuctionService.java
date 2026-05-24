package com.yala.service;
import com.yala.repository.*;
import com.yala.model.*;

import com.yala.auction.dto.AuctionResponse;
import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.auction.dto.CreateAuctionRequest;
import com.yala.repository.BidRepository;
import com.yala.event.AuctionFinishedEvent;
import com.yala.exceptions.DuplicateResourceException;
import com.yala.exceptions.InvalidBidException;
import com.yala.exceptions.ResourceNotFoundException;
import com.yala.exceptions.UnauthorizedException;
import com.yala.model.Listing;
import com.yala.repository.ListingRepository;
import com.yala.model.User;
import com.yala.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ModelMapper modelMapper;

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

        return modelMapper.map(saved, AuctionResponse.class);
    }

    @Transactional(readOnly = true)
    public AuctionResponse findById(Long id) {
        return modelMapper.map(findOrThrow(id), AuctionResponse.class);
    }

    @Transactional(readOnly = true)
    public Page<AuctionSummaryResponse> findAllActive(Pageable pageable) {
        return auctionRepository.findByStatus(AuctionStatus.ACTIVE, pageable)
                .map(a -> modelMapper.map(a, AuctionSummaryResponse.class));
    }

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
            eventPublisher.publishEvent(new AuctionFinishedEvent(auction.getId()));
        }
    }

    private Auction findOrThrow(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Auction not found with id: " + id));
    }
}
