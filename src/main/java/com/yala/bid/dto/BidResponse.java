package com.yala.bid.dto;

import com.yala.bid.Bid;
import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record BidResponse(
        Long id,
        Float amount,
        LocalDateTime placedAt,
        UserResponse bidder) {

    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                bid.getAmount(),
                bid.getPlacedAt(),
                bid.getBidder() != null ? UserResponse.from(bid.getBidder()) : null);
    }
}
