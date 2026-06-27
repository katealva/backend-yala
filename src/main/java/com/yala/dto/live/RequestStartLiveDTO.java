package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para iniciar una transmisión en vivo")
public record RequestStartLiveDTO(
        @Schema(description = "Título de la transmisión", example = "Ruptura de sobres Pokémon 151",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 2, max = 120) String title,

        @Schema(description = "URL de imagen de portada (opcional)")
        String coverImageUrl) {
}
