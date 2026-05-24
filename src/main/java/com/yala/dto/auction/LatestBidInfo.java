package com.yala.dto.auction;

import java.time.LocalDateTime;

public record LatestBidInfo(
        String user,
        Float amount,
        LocalDateTime placedAt) {
}
