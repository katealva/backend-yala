package com.yala.dto.auth;

import jakarta.validation.constraints.NotNull;

public record RequestRefreshTokenDTO(
        @NotNull String refreshToken) {
}
