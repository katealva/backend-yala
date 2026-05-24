package com.yala.dto.category;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestCategoryDTO(
        @NotNull @Size(min = 2, max = 80) String name,
        String description) {
}
