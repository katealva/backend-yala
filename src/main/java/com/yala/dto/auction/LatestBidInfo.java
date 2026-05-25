package com.yala.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Detalle de la puja más reciente (objeto anidado en AuctionUpdateMessage)")
public record LatestBidInfo(
        @Schema(description = "Nombre del usuario que hizo la puja", example = "collector_mx")
        String user,

        @Schema(description = "Monto de la puja en soles", example = "1350.00") Float amount,

        @Schema(description = "Fecha y hora en que se registró la puja",
                example = "2026-05-19T14:32:00")
        LocalDateTime placedAt) {
}
