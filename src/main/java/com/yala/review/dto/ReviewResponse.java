package com.yala.review.dto;

import com.yala.review.Review;
import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        UserResponse author) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                review.getAuthor() != null ? UserResponse.from(review.getAuthor()) : null);
    }
}
