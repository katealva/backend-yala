package com.yala.order.dto;

import com.yala.listing.dto.ListingResponse;
import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        Float amount,
        String status,
        LocalDateTime createdAt,
        ListingResponse listing,
        UserResponse buyer,
        UserResponse seller) {
}
