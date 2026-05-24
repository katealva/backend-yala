package com.yala.auction.dto;

import java.time.LocalDateTime;

public record LatestBidInfo(
        String user,
        Float amount,
        LocalDateTime placedAt) {
}
