package com.yala.dto.bid;

import com.yala.dto.user.ResponseUserDTO;
import java.time.LocalDateTime;

public record ResponseBidDTO(
        Long id,
        Float amount,
        LocalDateTime placedAt,
        ResponseUserDTO bidder) {
}
