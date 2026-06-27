package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Puja sobre una subasta flash de un live")
public record RequestLiveBidDTO(
        @Schema(description = "Monto de la puja en soles", example = "56.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) Float amount) {
}
