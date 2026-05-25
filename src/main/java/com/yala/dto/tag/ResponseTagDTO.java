package com.yala.dto.tag;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tag del catálogo de Yala")
public record ResponseTagDTO(
        @Schema(description = "ID del tag", example = "7") Long id,
        @Schema(description = "Nombre del tag", example = "holo") String name) {
}
