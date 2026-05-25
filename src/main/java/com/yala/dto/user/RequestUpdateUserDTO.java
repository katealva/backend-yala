package com.yala.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos editables del perfil del usuario autenticado. Los campos null se ignoran.")
public record RequestUpdateUserDTO(
        @Schema(description = "Nuevo nombre visible del usuario (entre 2 y 100 caracteres)",
                example = "Ana Torres")
        @Size(min = 2, max = 100) String name,

        @Schema(description = "URL pública del avatar del usuario",
                example = "https://yala-collectibles.s3.amazonaws.com/avatars/42.jpg")
        String avatarUrl) {
}
