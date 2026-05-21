package com.yala.listing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateListingRequest(
        @NotNull @Size(min = 3, max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull String mode,
        @Min(0) Float fixedPrice,
        @NotNull String condition,
        @NotNull Long categoryId,
        List<String> tags) {
}
