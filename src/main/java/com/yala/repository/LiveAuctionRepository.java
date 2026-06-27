package com.yala.repository;
import com.yala.model.*;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveAuctionRepository extends JpaRepository<LiveAuction, Long> {

    List<LiveAuction> findByLiveStreamId(Long liveStreamId);

    Optional<LiveAuction> findFirstByLiveStreamIdAndStatusOrderByStartedAtDesc(
            Long liveStreamId, LiveAuctionStatus status);
}
