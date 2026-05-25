package com.yala.dto.bid;

import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Puja registrada en una subasta")
public record ResponseBidDTO(
        @Schema(description = "ID de la puja", example = "9001") Long id,

        @Schema(description = "Monto de la puja en soles", example = "1400.00") Float amount,

        @Schema(description = "Fecha y hora exacta en que se registró la puja",
                example = "2026-05-19T14:32:00")
        LocalDateTime placedAt,

        @Schema(description = "Usuario que realizó la puja")
        ResponseUserDTO bidder) {
}
