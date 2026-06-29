package com.yala.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "Datos para editar una subasta existente: precio inicial y fecha de cierre")
public record RequestAuctionUpdateDTO(
        @Schema(description = "Precio inicial de la subasta en soles. Debe ser >= 0",
                example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) Float startingPrice,

        @Schema(description = "Fecha y hora en que termina la subasta (ISO-8601). Debe ser futura",
                example = "2026-07-15T20:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDateTime endsAt) {
}
