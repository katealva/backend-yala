package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Subasta flash de un live")
public record ResponseLiveAuctionDTO(
        @Schema(description = "ID de la subasta flash", example = "88") Long id,
        @Schema(description = "ID del live al que pertenece", example = "12") Long liveStreamId,
        @Schema(description = "Título del producto") String title,
        @Schema(description = "Precio base en soles", example = "50.00") Float basePrice,
        @Schema(description = "Incremento mínimo por puja", example = "1.00") Float bidIncrement,
        @Schema(description = "Precio actual (null si no hay pujas)", example = "55.00") Float currentPrice,
        @Schema(description = "Estado", example = "ACTIVE",
                allowableValues = {"ACTIVE", "SOLD", "DESERTED"}) String status,
        @Schema(description = "Nombre del ganador (si SOLD)") String winnerName,
        @Schema(description = "Total de pujas", example = "5") int totalBids,
        @Schema(description = "Fecha de inicio") LocalDateTime startedAt) {
}
