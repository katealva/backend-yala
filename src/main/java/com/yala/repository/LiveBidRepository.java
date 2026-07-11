package com.yala.repository;
import com.yala.model.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveBidRepository extends JpaRepository<LiveBid, Long> {

    /** Highest (and therefore winning) bid of a flash auction. */
    Optional<LiveBid> findFirstByLiveAuctionIdOrderByAmountDesc(Long liveAuctionId);

    List<LiveBid> findByLiveAuctionIdOrderByAmountDesc(Long liveAuctionId);

    Page<LiveBid> findByLiveAuctionId(Long liveAuctionId, Pageable pageable);

    long countByLiveAuctionId(Long liveAuctionId);

    /** All bids of a whole live (across its flash auctions), oldest first — for highlight scoring. */
    List<LiveBid> findByLiveAuction_LiveStream_IdOrderByPlacedAtAsc(Long liveStreamId);
}
