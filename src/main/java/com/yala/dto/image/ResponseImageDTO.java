package com.yala.dto.image;

public record ResponseImageDTO(
        Long id,
        String url,
        Integer sortOrder,
        Long listingId) {
}
