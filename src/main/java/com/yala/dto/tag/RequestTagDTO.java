package com.yala.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear o actualizar un tag del catálogo (uso restringido a ADMIN)")
public record RequestTagDTO(
        @Schema(description = "Nombre único del tag (2 a 50 caracteres). Convención: minúsculas, sin espacios",
                example = "holo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tag name is required")
        @Size(min = 2, max = 50, message = "Tag name must be between 2 and 50 characters")
        String name) {
}
