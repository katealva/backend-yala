package com.yala.dto.review;

import com.yala.dto.user.ResponseUserDTO;
import java.time.LocalDateTime;

public record ResponseReviewDTO(
        Long id,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        ResponseUserDTO author) {
}
