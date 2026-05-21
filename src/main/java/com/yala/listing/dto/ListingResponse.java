package com.yala.listing.dto;

import com.yala.auction.dto.AuctionSummaryResponse;
import com.yala.category.dto.CategoryResponse;
import com.yala.image.Image;
import com.yala.listing.Listing;
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

    public static ListingResponse from(Listing listing) {
        List<String> imageUrls = listing.getImages() != null
                ? listing.getImages().stream().map(Image::getUrl).toList()
                : List.of();
        return new ListingResponse(
                listing.getId(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getMode() != null ? listing.getMode().name() : null,
                listing.getFixedPrice(),
                listing.getCondition(),
                listing.getStatus() != null ? listing.getStatus().name() : null,
                listing.getCreatedAt(),
                listing.getSeller() != null ? UserResponse.from(listing.getSeller()) : null,
                listing.getCategory() != null ? CategoryResponse.from(listing.getCategory()) : null,
                imageUrls,
                listing.getAuction() != null
                        ? AuctionSummaryResponse.from(listing.getAuction())
                        : null);
    }
}
