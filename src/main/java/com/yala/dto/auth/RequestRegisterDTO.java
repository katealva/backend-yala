package com.yala.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar un comprador. El DNI se valida contra RENIEC (JSON.pe) "
        + "y los nombres/apellidos deben coincidir. Siempre crea un usuario con rol USER.")
public record RequestRegisterDTO(
        @Schema(description = "DNI peruano de 8 dígitos", example = "12345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 dígitos") String dni,

        @Schema(description = "Email del usuario. Debe ser único en el sistema", example = "ana@yala.pe",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Email String email,

        @Schema(description = "Contraseña en texto plano (mínimo 8 caracteres). Se almacena hasheada con BCrypt",
                example = "supersegura", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 8) String password,

        @Schema(description = "Nombres (como en RENIEC)", example = "ANA MARIA",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 1, max = 100) String nombres,

        @Schema(description = "Apellido paterno (como en RENIEC)", example = "TORRES",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 1, max = 60) String apellidoPaterno,

        @Schema(description = "Apellido materno (como en RENIEC)", example = "QUISPE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 1, max = 60) String apellidoMaterno) {
}
