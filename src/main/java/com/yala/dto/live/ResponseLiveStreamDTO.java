package com.yala.dto.live;

import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Detalle de una transmisión en vivo, con la subasta flash activa si la hay")
public record ResponseLiveStreamDTO(
        @Schema(description = "ID de la transmisión", example = "12") Long id,
        @Schema(description = "Título") String title,
        @Schema(description = "Estado", example = "LIVE", allowableValues = {"LIVE", "ENDED"}) String status,
        @Schema(description = "Nombre de la room LiveKit") String roomName,
        @Schema(description = "URL de portada") String coverImageUrl,
        @Schema(description = "Fecha de inicio") LocalDateTime startedAt,
        @Schema(description = "Fecha de fin (si terminó)") LocalDateTime endedAt,
        @Schema(description = "Vendedor que transmite") ResponseUserDTO seller,
        @Schema(description = "Subasta flash activa (null si no hay)") ResponseLiveAuctionDTO activeAuction) {
}
