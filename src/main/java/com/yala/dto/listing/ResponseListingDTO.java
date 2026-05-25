package com.yala.dto.listing;

import com.yala.dto.auction.ResponseAuctionSummaryDTO;
import com.yala.dto.category.ResponseCategoryDTO;
import com.yala.dto.user.ResponseUserDTO;
import java.time.LocalDateTime;
import java.util.List;

public record ResponseListingDTO(
        Long id,
        String title,
        String description,
        String mode,
        Float fixedPrice,
        String condition,
        String status,
        LocalDateTime createdAt,
        ResponseUserDTO seller,
        ResponseCategoryDTO category,
        List<String> imageUrls,
        ResponseAuctionSummaryDTO auction) {
}
