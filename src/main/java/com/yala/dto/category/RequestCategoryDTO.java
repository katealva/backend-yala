package com.yala.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear o actualizar una categoría del catálogo (uso restringido a ADMIN)")
public record RequestCategoryDTO(
        @Schema(description = "Nombre único de la categoría (2 a 80 caracteres)",
                example = "Pokémon TCG", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 2, max = 80) String name,

        @Schema(description = "Descripción opcional de la categoría",
                example = "Cartas oficiales del Trading Card Game de Pokémon")
        String description) {
}
