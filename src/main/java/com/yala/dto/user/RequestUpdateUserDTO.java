package com.yala.dto.user;

import jakarta.validation.constraints.Size;

public record RequestUpdateUserDTO(
        @Size(min = 2, max = 100) String name,
        String avatarUrl) {
}
