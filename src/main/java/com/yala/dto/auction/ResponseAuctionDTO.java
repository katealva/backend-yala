package com.yala.dto.auction;

import com.yala.dto.listing.ResponseListingSummaryDTO;
import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Detalle completo de una subasta")
public record ResponseAuctionDTO(
        @Schema(description = "ID de la subasta", example = "501") Long id,

        @Schema(description = "Precio inicial con el que abrió la subasta", example = "100.00")
        Float startingPrice,

        @Schema(description = "Precio actual (= la puja más alta válida hasta el momento)", example = "1350.00")
        Float currentPrice,

        @Schema(description = "Fecha en que se inició la subasta", example = "2026-05-01T10:00:00")
        LocalDateTime startedAt,

        @Schema(description = "Fecha en que termina/terminó la subasta", example = "2026-06-15T20:00:00")
        LocalDateTime endsAt,

        @Schema(description = "Estado actual de la subasta", example = "ACTIVE",
                allowableValues = {"ACTIVE", "FINISHED", "CANCELLED"})
        String status,

        @Schema(description = "Usuario ganador (presente sólo si status = FINISHED y hubo pujas)")
        ResponseUserDTO winner,

        @Schema(description = "Número total de pujas registradas", example = "12") int totalBids,

        @Schema(description = "Resumen del listing (imágenes, título, condición, categoría, vendedor)")
        ResponseListingSummaryDTO listing) {
}
