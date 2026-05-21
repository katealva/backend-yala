package com.yala.order.dto;

import com.yala.listing.dto.ListingResponse;
import com.yala.order.Order;
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

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getCreatedAt(),
                order.getListing() != null ? ListingResponse.from(order.getListing()) : null,
                order.getBuyer() != null ? UserResponse.from(order.getBuyer()) : null,
                order.getSeller() != null ? UserResponse.from(order.getSeller()) : null);
    }
}
