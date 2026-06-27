package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Comentario del chat de un live")
public record ResponseLiveCommentDTO(
        @Schema(description = "ID del comentario", example = "4400") Long id,
        @Schema(description = "Texto") String text,
        @Schema(description = "Fecha") LocalDateTime createdAt,
        @Schema(description = "Nombre del autor", example = "ash_ketchum") String userName,
        @Schema(description = "ID del autor", example = "7") Long userId) {
}
