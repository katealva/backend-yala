package com.yala.dto.auth;

import com.yala.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para registrar un nuevo usuario y obtener tokens JWT")
public record RequestRegisterDTO(
        @Schema(description = "Nombre visible del usuario (2 a 100 caracteres)", example = "Ana Torres",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 2, max = 100) String name,

        @Schema(description = "Email del usuario. Debe ser único en el sistema", example = "ana@yala.pe",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Email String email,

        @Schema(description = "Contraseña en texto plano (mínimo 8 caracteres). Se almacena hasheada con BCrypt",
                example = "supersegura", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 8) String password,

        @Schema(description = "Rol solicitado. Si se omite, se asigna USER por defecto",
                example = "USER", defaultValue = "USER")
        Role role) {

    /** Defaults the role to {@link Role#USER} when the client omits it. */
    public RequestRegisterDTO {
        if (role == null) {
            role = Role.USER;
        }
    }
}
