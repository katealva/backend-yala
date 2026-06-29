package com.yala.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Restablece la contraseña usando el código enviado por correo")
public record RequestResetPasswordDTO(
        @Schema(description = "Email de la cuenta", example = "ana@yala.pe")
        @NotNull @Email String email,

        @Schema(description = "Código de 6 dígitos recibido por correo", example = "123456")
        @NotNull String code,

        @Schema(description = "Nueva contraseña (mínimo 8 caracteres)", example = "nuevaClave123")
        @NotNull @Size(min = 8) String newPassword) {
}
