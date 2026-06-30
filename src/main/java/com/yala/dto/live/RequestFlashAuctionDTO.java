package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una subasta flash dentro de un live")
public record RequestFlashAuctionDTO(
        @Schema(description = "Título del producto", example = "Charizard PSA 9",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 2, max = 120) String title,

        @Schema(description = "Precio base del producto en soles (hasta 2 decimales, ≤9999.99)", example = "50.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Digits(integer = 4, fraction = 2) Float basePrice,

        @Schema(description = "Monto en que sube cada puja (por defecto 1 sol, hasta 2 decimales)", example = "1.00")
        @Min(0) @Digits(integer = 4, fraction = 2) Float bidIncrement) {
}
