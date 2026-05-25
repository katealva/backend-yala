package com.yala.dto.review;

import com.yala.dto.user.ResponseUserDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Reseña dejada por un comprador o vendedor tras una orden CONFIRMED")
public record ResponseReviewDTO(
        @Schema(description = "ID de la reseña", example = "3001") Long id,

        @Schema(description = "Calificación (1-5)", example = "5") Integer rating,

        @Schema(description = "Comentario del autor",
                example = "Excelente vendedor, envío rápido y producto tal como se describe.")
        String comment,

        @Schema(description = "Fecha en que se creó la reseña", example = "2026-05-21T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "Usuario que escribió la reseña")
        ResponseUserDTO author) {
}
