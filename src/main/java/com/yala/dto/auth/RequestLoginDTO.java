package com.yala.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Credenciales para iniciar sesión y obtener tokens JWT")
public record RequestLoginDTO(
        @Schema(description = "Email del usuario registrado", example = "collector@yala.pe",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Email String email,

        @Schema(description = "Contraseña en texto plano (se valida contra el hash BCrypt almacenado)",
                example = "secret1234", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull String password) {
}
