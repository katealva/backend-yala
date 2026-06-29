package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Resumen con IA de los comentarios del chat de un live (solo el host)")
public record ResponseLiveCommentSummaryDTO(
        @Schema(description = "Resumen generado (3-5 viñetas)") String summary,
        @Schema(description = "Cantidad de comentarios analizados", example = "37") int commentCount,
        @Schema(description = "Momento de generación") LocalDateTime generatedAt) {
}
