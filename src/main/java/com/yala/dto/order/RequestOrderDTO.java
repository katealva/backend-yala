package com.yala.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para crear una orden de compra directa sobre un listing de precio fijo")
public record RequestOrderDTO(
        @Schema(description = "ID del listing a comprar (mode debe ser FIXED y status ACTIVE)",
                example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long listingId) {
}
