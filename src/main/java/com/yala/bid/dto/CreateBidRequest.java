package com.yala.bid.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBidRequest(
        @NotNull Long auctionId,
        @NotNull @Min(0) Float amount) {
}
