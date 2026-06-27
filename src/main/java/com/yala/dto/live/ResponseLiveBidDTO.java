package com.yala.dto.live;

import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Puja registrada en una subasta flash")
public record ResponseLiveBidDTO(
        @Schema(description = "ID de la puja", example = "9100") Long id,
        @Schema(description = "Monto en soles", example = "56.00") Float amount,
        @Schema(description = "Fecha de la puja") LocalDateTime placedAt,
        @Schema(description = "Usuario que pujó") ResponseUserDTO bidder) {
}
