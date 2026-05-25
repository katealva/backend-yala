package com.yala.dto.user;

import com.yala.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Información pública de un usuario (no incluye passwordHash ni datos sensibles)")
public record ResponseUserDTO(
        @Schema(description = "ID interno del usuario", example = "42")
        Long id,

        @Schema(description = "Nombre visible del usuario", example = "Ana Torres")
        String name,

        @Schema(description = "Email del usuario", example = "ana@yala.pe")
        String email,

        @Schema(description = "URL del avatar (puede ser null)",
                example = "https://yala-collectibles.s3.amazonaws.com/avatars/42.jpg")
        String avatarUrl,

        @Schema(description = "Reputación calculada como promedio de ratings de reseñas recibidas (0.0 - 5.0)",
                example = "4.75")
        Float reputation,

        @Schema(description = "Indica si el usuario es vendedor verificado y puede crear listings/auctions",
                example = "true")
        Boolean isVerifiedSeller,

        @Schema(description = "Rol del usuario (USER, SELLER, ADMIN)", example = "SELLER")
        Role role) {
}
