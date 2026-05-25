package com.yala.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Resumen ligero de una subasta para incluir embebido en listados y otros DTOs")
public record ResponseAuctionSummaryDTO(
        @Schema(description = "ID de la subasta", example = "501") Long id,

        @Schema(description = "Precio actual (última puja válida)", example = "1350.00")
        Float currentPrice,

        @Schema(description = "Fecha y hora en que termina la subasta", example = "2026-06-15T20:00:00")
        LocalDateTime endsAt,

        @Schema(description = "Estado actual de la subasta", example = "ACTIVE",
                allowableValues = {"ACTIVE", "FINISHED", "CANCELLED"})
        String status) {
}
