package com.yala.dto.order;

import com.yala.dto.listing.ResponseListingDTO;
import com.yala.dto.user.ResponseUserDTO;
import java.time.LocalDateTime;

public record ResponseOrderDTO(
        Long id,
        Float amount,
        String status,
        LocalDateTime createdAt,
        ResponseListingDTO listing,
        ResponseUserDTO buyer,
        ResponseUserDTO seller) {
}
