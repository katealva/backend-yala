package com.yala.image.dto;

public record ImageResponse(
        Long id,
        String url,
        Integer sortOrder,
        Long listingId) {
}
