package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mensaje WebSocket en /topic/live/{id} con cambios de estado del live y su subasta flash")
public record LiveUpdateMessage(
        @Schema(description = "Tipo de evento", example = "BID",
                allowableValues = {"AUCTION_STARTED", "BID", "AUCTION_CLOSED", "LIVE_ENDED"})
        String type,
        @Schema(description = "Estado de la subasta flash relacionada (puede ser null)")
        ResponseLiveAuctionDTO auction) {
}
