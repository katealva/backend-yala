package com.yala.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestTagDTO(
        @NotBlank(message = "Tag name is required")
        @Size(min = 2, max = 50, message = "Tag name must be between 2 and 50 characters")
        String name) {
}
