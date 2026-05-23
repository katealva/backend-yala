package com.yala.image.dto;

import com.yala.image.Image;

public record ImageResponse(Long id, String url, Integer sortOrder, Long listingId) {

    public static ImageResponse from(Image image) {
        return new ImageResponse(
                image.getId(),
                image.getUrl(),
                image.getSortOrder(),
                image.getListing() != null ? image.getListing().getId() : null);
    }
}
