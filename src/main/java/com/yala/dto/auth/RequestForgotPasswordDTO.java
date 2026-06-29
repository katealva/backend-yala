package com.yala.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Solicita un código para restablecer la contraseña")
public record RequestForgotPasswordDTO(
        @Schema(description = "Email de la cuenta", example = "ana@yala.pe")
        @NotNull @Email String email) {
}
