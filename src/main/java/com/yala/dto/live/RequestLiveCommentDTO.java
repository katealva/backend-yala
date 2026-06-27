package com.yala.dto.live;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Comentario en el chat de un live")
public record RequestLiveCommentDTO(
        @Schema(description = "Texto del comentario", example = "Vamos por ese Charizard",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Size(min = 1, max = 500) String text) {
}
