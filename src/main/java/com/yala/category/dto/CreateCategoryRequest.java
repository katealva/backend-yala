package com.yala.category.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotNull @Size(min = 2, max = 80) String name,
        String description) {
}
