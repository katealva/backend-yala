package com.yala.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una reseña sobre una orden CONFIRMED. La reputación del destinatario se recalcula automáticamente.")
public record RequestReviewDTO(
        @Schema(description = "ID de la orden sobre la que se opina (debe estar en estado CONFIRMED)",
                example = "7001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long orderId,

        @Schema(description = "Calificación entera entre 1 y 5",
                example = "5", minimum = "1", maximum = "5",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(5) Integer rating,

        @Schema(description = "Comentario opcional (máximo 1000 caracteres)",
                example = "Excelente vendedor, envío rápido y producto tal como se describe.")
        @Size(max = 1000) String comment) {
}
