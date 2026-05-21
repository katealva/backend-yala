package com.yala.auction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    List<Auction> findByStatus(AuctionStatus status);

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    /** Used by the scheduler to find auctions that must be auto-closed. */
    List<Auction> findByStatusAndEndsAtBefore(AuctionStatus status, LocalDateTime time);

    Optional<Auction> findByListingId(Long listingId);
}
