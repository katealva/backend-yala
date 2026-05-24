package com.yala.bid.dto;

import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record BidResponse(
        Long id,
        Float amount,
        LocalDateTime placedAt,
        UserResponse bidder) {
}
