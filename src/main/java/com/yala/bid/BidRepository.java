package com.yala.bid;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    /** Highest (and therefore winning) bid of an auction. */
    Optional<Bid> findFirstByAuctionIdOrderByAmountDesc(Long auctionId);

    List<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId);

    Page<Bid> findByAuctionId(Long auctionId, Pageable pageable);

    long countByAuctionId(Long auctionId);
}
