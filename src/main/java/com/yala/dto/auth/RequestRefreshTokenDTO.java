package com.yala.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Petición para renovar el access token a partir de un refresh token válido")
public record RequestRefreshTokenDTO(
        @Schema(description = "Refresh token JWT emitido previamente al hacer login o registro",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull String refreshToken) {
}
