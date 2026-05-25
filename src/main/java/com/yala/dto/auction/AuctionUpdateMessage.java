package com.yala.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mensaje WebSocket broadcast en /topic/auction/{id} cuando hay una nueva puja o la subasta termina")
public record AuctionUpdateMessage(
        @Schema(description = "ID de la subasta", example = "501") Long auctionId,

        @Schema(description = "Precio actual tras la última puja", example = "1350.00") Float currentPrice,

        @Schema(description = "Total de pujas registradas", example = "12") Integer totalBids,

        @Schema(description = "Estado de la subasta al momento del mensaje", example = "ACTIVE",
                allowableValues = {"ACTIVE", "FINISHED", "CANCELLED"})
        String status,

        @Schema(description = "Detalle de la última puja (puede ser null si aún no hay pujas)")
        LatestBidInfo latestBid,

        @Schema(description = "Nombre del ganador (presente sólo cuando status = FINISHED)",
                example = "collector_mx")
        String winnerUsername) {
}
