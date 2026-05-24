package com.yala.review.dto;

import com.yala.user.dto.UserResponse;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        UserResponse author) {
}
