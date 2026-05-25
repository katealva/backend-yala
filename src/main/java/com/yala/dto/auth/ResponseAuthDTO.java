package com.yala.dto.auth;

import com.yala.model.Role;
import com.yala.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de autenticación con tokens JWT y datos básicos del usuario")
public record ResponseAuthDTO(
        @Schema(description = "Access token JWT (expira en 1 hora). Enviar en el header Authorization: Bearer <token>",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Refresh token JWT (expira en 7 días). Usar para obtener un nuevo access token",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "ID interno del usuario autenticado", example = "42")
        Long userId,

        @Schema(description = "Email del usuario autenticado", example = "ana@yala.pe")
        String email,

        @Schema(description = "Nombre visible del usuario", example = "Ana Torres")
        String name,

        @Schema(description = "Rol del usuario (USER, SELLER, ADMIN)", example = "USER")
        Role role) {

    public static ResponseAuthDTO of(User user, String accessToken, String refreshToken) {
        return new ResponseAuthDTO(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole());
    }
}
