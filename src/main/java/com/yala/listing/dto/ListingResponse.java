package com.yala.listing.dto;

import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.category.dto.CategoryResponse;
import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ListingResponse(
        Long id,
        String title,
        String description,
        String mode,
        Float fixedPrice,
        String condition,
        String status,
        LocalDateTime createdAt,
        UserResponse seller,
        CategoryResponse category,
        List<String> imageUrls,
        AuctionSummaryResponse auction) {
}
