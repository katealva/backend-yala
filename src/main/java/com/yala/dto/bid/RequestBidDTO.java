package com.yala.dto.bid;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para registrar una nueva puja en una subasta activa")
public record RequestBidDTO(
        @Schema(description = "ID de la subasta a la que se puja",
                example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long auctionId,

        @Schema(description = "Monto de la puja en soles. Debe ser estrictamente mayor que currentPrice",
                example = "1400.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) Float amount) {
}
