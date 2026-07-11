package com.yala.dto.appraisal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resultado del "Agente de Tasación por Foto" (solo cartas TCG). La IA identifica la carta y JustTCG
 * aporta el precio real de mercado; {@code pricing} es null cuando no hay match o la foto no es una carta.
 */
@Schema(description = "Identificación + tasación de una carta TCG a partir de su foto")
public record ResponseAppraisalDTO(
        @Schema(description = "Categoría detectada", example = "tcg",
                allowableValues = {"tcg", "unknown"})
        String category,
        @Schema(description = "Juego / franquicia", example = "Pokémon") String franchise,
        @Schema(description = "Nombre de la carta identificado por la IA", example = "Charizard") String character,
        @Schema(description = "Variante visible (edición, rareza)", example = "Base Set Holo") String variant,
        @Schema(description = "Confianza de la identificación (0-1)", example = "0.82") double confidence,
        @Schema(description = "true si es una carta TCG reconocible en la foto", example = "true")
        boolean recognizable,
        @Schema(description = "Precio real de JustTCG (null si no hay match)") Pricing pricing,
        @Schema(description = "Nota breve para el usuario") String note) {

    @Schema(description = "Rango de precio real y comparables (fuente: JustTCG, USD)")
    public record Pricing(
            @Schema(description = "Nombre de la carta encontrada en JustTCG") String itemName,
            @Schema(description = "Juego (slug JustTCG)") String game,
            @Schema(description = "Precio mínimo") double priceMin,
            @Schema(description = "Precio máximo") double priceMax,
            @Schema(description = "Moneda", example = "USD") String currency,
            @Schema(description = "Comparables de ejemplo") List<Comparable> comparables) {
    }

    @Schema(description = "Comparable individual")
    public record Comparable(
            @Schema(description = "Descripción (set · condición · edición)") String title,
            @Schema(description = "Precio") double price) {
    }

    public static ResponseAppraisalDTO unrecognizable(String note) {
        return new ResponseAppraisalDTO("unknown", null, null, null, 0.0, false, null, note);
    }
}
