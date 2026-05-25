package com.yala.dto.listing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Datos para crear o actualizar un listing (publicación) en Yala")
public record RequestListingDTO(
        @Schema(description = "Título del listing (3 a 200 caracteres)",
                example = "Charizard PSA 9 — 1st Edition", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 3, max = 200) String title,

        @Schema(description = "Descripción larga del producto (máximo 2000 caracteres)",
                example = "Carta en excelente estado, certificada por PSA con grado 9. Sin marcas ni dobleces.")
        @Size(max = 2000) String description,

        @Schema(description = "Modo de venta. FIXED = precio fijo, AUCTION = subasta",
                example = "AUCTION", allowableValues = {"FIXED", "AUCTION"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull String mode,

        @Schema(description = "Precio fijo en soles (solo si mode = FIXED, ignorado en AUCTION)",
                example = "499.90")
        @Min(0) Float fixedPrice,

        @Schema(description = "Estado físico del coleccionable",
                example = "PSA 9 — Mint", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull String condition,

        @Schema(description = "ID de la categoría a la que pertenece el listing",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long categoryId,

        @Schema(description = "Lista opcional de nombres de tags a asociar (se crean si no existen)",
                example = "[\"holo\",\"1st-edition\",\"mint\"]")
        List<String> tags) {
}
