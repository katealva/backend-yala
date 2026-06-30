package com.yala.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "Datos para crear una nueva subasta sobre un listing existente")
public record RequestAuctionDTO(
        @Schema(description = "ID del listing al que se asocia la subasta",
                example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long listingId,

        @Schema(description = "Precio inicial de la subasta en soles. >= 0, hasta 2 decimales (≤9999.99)",
                example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Digits(integer = 4, fraction = 2) Float startingPrice,

        @Schema(description = "Fecha y hora en que termina la subasta (ISO-8601). Debe ser futura",
                example = "2026-06-15T20:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDateTime endsAt) {
}
