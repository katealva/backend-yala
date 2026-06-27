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
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/** Builds the Live* response DTOs, reusing the shared {@link ModelMapper} for nested users. */
@Component
@RequiredArgsConstructor
public class LiveMapper {

    private final ModelMapper modelMapper;

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
                modelMapper.map(s.getSeller(), ResponseUserDTO.class),
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
                modelMapper.map(b.getBidder(), ResponseUserDTO.class));
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
