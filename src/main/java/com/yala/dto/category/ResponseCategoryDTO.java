package com.yala.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Categoría del catálogo de Yala")
public record ResponseCategoryDTO(
        @Schema(description = "ID interno de la categoría", example = "1")
        Long id,

        @Schema(description = "Nombre de la categoría", example = "Pokémon TCG")
        String name,

        @Schema(description = "Descripción de la categoría",
                example = "Cartas oficiales del Trading Card Game de Pokémon")
        String description) {
}
