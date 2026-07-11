package com.yala.dto.appraisal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identificación estructurada que devuelve el "Agente de Tasación por Foto".
 * El match contra el dataset (rango de precio + comparables) lo resuelve el frontend.
 */
@Schema(description = "Identificación estructurada de un coleccionable a partir de su foto")
public record ResponseAppraisalDTO(
        @Schema(description = "Categoría detectada", example = "tcg",
                allowableValues = {"funko", "nendoroid", "manga", "comic", "tcg", "unknown"})
        String category,
        @Schema(description = "Franquicia / saga", example = "Pokémon") String franchise,
        @Schema(description = "Personaje o título", example = "Charizard") String character,
        @Schema(description = "Variante visible (edición, rareza, exclusividad)", example = "Base Set Holo 1st Edition")
        String variant,
        @Schema(description = "Confianza de la identificación (0-1)", example = "0.82") double confidence,
        @Schema(description = "true si es un coleccionable reconocible en la foto", example = "true")
        boolean recognizable,
        @Schema(description = "Nota breve para el usuario (p. ej. por qué no se pudo identificar)")
        String note) {

    public static ResponseAppraisalDTO unrecognizable(String note) {
        return new ResponseAppraisalDTO("unknown", null, null, null, 0.0, false, note);
    }
}
