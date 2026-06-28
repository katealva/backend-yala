package com.yala.service;

import com.yala.dto.live.ResponseLiveAuctionDTO;
import com.yala.dto.live.ResponseLiveBidDTO;
import com.yala.dto.live.ResponseLiveCommentDTO;
import com.yala.dto.live.ResponseLiveStreamDTO;
import com.yala.dto.live.ResponseLiveSummaryDTO;
import com.yala.dto.user.ResponseUserDTO;
import com.yala.model.LiveAuction;
import com.yala.model.LiveBid;
import com.yala.model.LiveComment;
import com.yala.model.LiveStream;
import com.yala.model.User;
import org.springframework.stereotype.Component;

/** Builds the Live* response DTOs. Nested users are mapped by hand to avoid ModelMapper choking on
 *  Hibernate lazy proxies when targeting the immutable {@link ResponseUserDTO} record. */
@Component
public class LiveMapper {

    /** Null-safe User -> ResponseUserDTO. Getters initialize the lazy proxy inside the caller's tx. */
    private ResponseUserDTO toUser(User u) {
        if (u == null) return null;
        return new ResponseUserDTO(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getAvatarUrl(),
                u.getReputation(),
                u.getIsVerifiedSeller(),
                u.getIsIdentityVerified(),
                u.getRole());
    }

    public ResponseLiveSummaryDTO toSummary(LiveStream s) {
        if (s == null) return null;
        return new ResponseLiveSummaryDTO(
                s.getId(),
                s.getTitle(),
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getCoverImageUrl(),
                s.getSeller() != null ? s.getSeller().getName() : null,
                s.getSeller() != null ? s.getSeller().getId() : null,
                s.getStartedAt());
    }

    public ResponseLiveStreamDTO toStreamDetail(LiveStream s, ResponseLiveAuctionDTO activeAuction) {
        if (s == null) return null;
        return new ResponseLiveStreamDTO(
                s.getId(),
                s.getTitle(),
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getRoomName(),
                s.getCoverImageUrl(),
                s.getStartedAt(),
                s.getEndedAt(),
                toUser(s.getSeller()),
                activeAuction);
    }

    public ResponseLiveAuctionDTO toAuctionDto(LiveAuction a, long totalBids) {
        if (a == null) return null;
        return new ResponseLiveAuctionDTO(
                a.getId(),
                a.getLiveStream() != null ? a.getLiveStream().getId() : null,
                a.getTitle(),
                a.getBasePrice(),
                a.getBidIncrement(),
                a.getCurrentPrice(),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getWinner() != null ? a.getWinner().getName() : null,
                (int) totalBids,
                a.getStartedAt());
    }

    public ResponseLiveBidDTO toBidDto(LiveBid b) {
        if (b == null) return null;
        return new ResponseLiveBidDTO(
                b.getId(),
                b.getAmount(),
                b.getPlacedAt(),
                toUser(b.getBidder()));
    }

    public ResponseLiveCommentDTO toCommentDto(LiveComment c) {
        if (c == null) return null;
        return new ResponseLiveCommentDTO(
                c.getId(),
                c.getText(),
                c.getCreatedAt(),
                c.getUser() != null ? c.getUser().getName() : null,
                c.getUser() != null ? c.getUser().getId() : null);
    }
}
