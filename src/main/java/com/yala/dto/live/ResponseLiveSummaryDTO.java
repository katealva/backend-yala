package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Resumen de una transmisión en vivo (para el carrusel de la home)")
public record ResponseLiveSummaryDTO(
        @Schema(description = "ID de la transmisión", example = "12") Long id,
        @Schema(description = "Título", example = "Ruptura de sobres Pokémon 151") String title,
        @Schema(description = "Estado", example = "LIVE", allowableValues = {"LIVE", "ENDED"}) String status,
        @Schema(description = "URL de portada") String coverImageUrl,
        @Schema(description = "Nombre del vendedor", example = "collector_mx") String sellerName,
        @Schema(description = "ID del vendedor", example = "3") Long sellerId,
        @Schema(description = "Fecha de inicio") LocalDateTime startedAt) {
}
